package com.jreq.request.infrastructure.http;

import com.jreq.request.application.CookieJarEdit;
import com.jreq.request.application.CookieJarState;
import com.jreq.request.domain.CookieExpiration;
import com.jreq.request.domain.CookieIdentity;
import com.jreq.request.domain.StoredCookie;
import org.junit.jupiter.api.Test;

import java.net.HttpCookie;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ManagedCookieStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-03T20:00:00Z");
    private static final URI HTTPS_USERS = URI.create("https://api.example.com/users/profile");

    private final ManagedCookieStore store = new ManagedCookieStore(
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void matchesHostOnlyDomainPathAndSecureBoundaries() {
        store.restore(List.of(
                cookie("host", "api.example.com", "/", true, false),
                cookie("domain", "example.com", "/", false, false),
                cookie("admin", "api.example.com", "/admin", true, false),
                cookie("secure", "api.example.com", "/", true, true)));

        assertThat(names(store.matching(HTTPS_USERS)))
                .containsExactlyInAnyOrder("host", "domain", "secure");
        assertThat(names(store.matching(URI.create("https://child.api.example.com/users"))))
                .containsExactly("domain");
        assertThat(names(store.matching(URI.create("https://badexample.com/users")))).isEmpty();
        assertThat(names(store.matching(URI.create("http://api.example.com/users"))))
                .containsExactlyInAnyOrder("host", "domain");
        assertThat(names(store.matching(URI.create("https://api.example.com/administrator"))))
                .doesNotContain("admin");
        assertThat(names(store.matching(URI.create("https://api.example.com/admin/users"))))
                .contains("admin");
    }

    @Test
    void derivesDefaultPathAndTreatsPortsAsTheSameCookieScope() {
        HttpCookie cookie = new HttpCookie("session", "value");

        store.add(URI.create("https://example.com:8443/account/login"), cookie);

        assertThat(store.snapshot()).singleElement()
                .extracting(StoredCookie::domain, StoredCookie::path, StoredCookie::hostOnly)
                .containsExactly("example.com", "/account", true);
        assertThat(names(store.matching(URI.create("https://example.com:9443/account/profile"))))
                .containsExactly("session");
        assertThat(store.matching(URI.create("https://example.com:9443/other"))).isEmpty();
    }

    @Test
    void replacesIdentityDeletesMaxAgeZeroAndPurgesExpiredCookies() {
        HttpCookie first = new HttpCookie("session", "first");
        first.setPath("/");
        first.setMaxAge(60);
        store.add(HTTPS_USERS, first);
        HttpCookie replacement = new HttpCookie("session", "second");
        replacement.setPath("/");
        replacement.setMaxAge(120);
        store.add(HTTPS_USERS, replacement);

        assertThat(store.snapshot()).singleElement()
                .extracting(StoredCookie::value)
                .isEqualTo("second");

        HttpCookie deletion = new HttpCookie("session", "ignored-secret");
        deletion.setPath("/");
        deletion.setMaxAge(0);
        store.add(HTTPS_USERS, deletion);

        assertThat(store.snapshot()).isEmpty();
    }

    @Test
    void acceptParsedStoresFutureExpiresAndDeletesMaxAgeZero() {
        CookieHeaderParser parser = new CookieHeaderParser();
        CookieHeaderParser.ParsedCookie persistent = parser.parseSetCookie(
                "session=abc; Path=/; Expires=Wed, 18 Aug 2027 12:00:00 GMT",
                HTTPS_USERS,
                NOW).orElseThrow();
        store.acceptParsed(HTTPS_USERS, persistent);

        assertThat(store.snapshot()).singleElement().satisfies(cookie -> {
            assertThat(cookie.name()).isEqualTo("session");
            assertThat(cookie.value()).isEqualTo("abc");
            assertThat(cookie.expiration()).isInstanceOf(CookieExpiration.At.class);
        });

        CookieHeaderParser.ParsedCookie deletion = parser.parseSetCookie(
                "session=gone; Path=/; Max-Age=0",
                HTTPS_USERS,
                NOW).orElseThrow();
        store.acceptParsed(HTTPS_USERS, deletion);
        assertThat(store.snapshot()).isEmpty();
    }

    @Test
    void applyEditMergesUpsertsAndDeletionsWithoutClearingUntouchedCookies() {
        StoredCookie baseline = cookie("keep", "api.example.com", "/", true, false);
        StoredCookie remove = cookie("drop", "api.example.com", "/", true, false);
        store.restore(List.of(baseline, remove));
        long revisionBefore = store.state().revision();

        StoredCookie added = cookie("new", "api.example.com", "/", true, false);
        store.applyEdit(CookieJarEdit.of(
                List.of(added),
                Set.of(new CookieIdentity("drop", "api.example.com", "/")),
                false));

        assertThat(names(store.snapshot())).containsExactlyInAnyOrder("keep", "new");
        assertThat(store.state().revision()).isGreaterThan(revisionBefore);
    }

    @Test
    void tracksRevisionsAndKeepsSessionCookiesOutOfPersistentSnapshots() {
        store.add(HTTPS_USERS, new HttpCookie("session", "memory-only"));
        HttpCookie persistent = new HttpCookie("remember", "persisted");
        persistent.setMaxAge(3600);
        store.add(HTTPS_USERS, persistent);

        CookieJarState state = store.state();

        assertThat(state.dirty()).isTrue();
        assertThat(state.cookies()).hasSize(2);
        assertThat(state.persistentCookies()).singleElement()
                .extracting(StoredCookie::name)
                .isEqualTo("remember");

        store.markPersisted(state.revision());
        assertThat(store.state().dirty()).isFalse();
    }

    @Test
    void enforcesCookieSizePerDomainAndTotalBounds() {
        HttpCookie oversized = new HttpCookie("oversized", "x".repeat(ManagedCookieStore.MAX_COOKIE_BYTES));
        store.add(HTTPS_USERS, oversized);
        assertThat(store.snapshot()).isEmpty();

        for (int index = 0; index < ManagedCookieStore.MAX_COOKIES_PER_DOMAIN + 25; index++) {
            store.add(HTTPS_USERS, new HttpCookie("cookie-" + index, "v"));
        }
        assertThat(store.snapshot()).hasSize(ManagedCookieStore.MAX_COOKIES_PER_DOMAIN);
        assertThat(names(store.snapshot())).contains("cookie-" + (ManagedCookieStore.MAX_COOKIES_PER_DOMAIN + 24));

        for (int domain = 0; domain < 30; domain++) {
            URI uri = URI.create("https://host" + domain + ".example.net/");
            for (int index = 0; index < ManagedCookieStore.MAX_COOKIES_PER_DOMAIN; index++) {
                store.add(uri, new HttpCookie("c" + index, "v"));
            }
        }
        assertThat(store.snapshot()).hasSizeLessThanOrEqualTo(ManagedCookieStore.MAX_COOKIES_TOTAL);
    }

    @Test
    void remainsConsistentUnderConcurrentReadsAndWrites() {
        int workers = 8;
        int operations = 400;
        CountDownLatch start = new CountDownLatch(1);

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(8), () -> {
            try (var executor = Executors.newFixedThreadPool(workers)) {
                List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                for (int worker = 0; worker < workers; worker++) {
                    int workerId = worker;
                    futures.add(executor.submit(() -> {
                        start.await();
                        for (int index = 0; index < operations; index++) {
                            HttpCookie cookie = new HttpCookie("w" + workerId + "-" + index, "v");
                            store.add(HTTPS_USERS, cookie);
                            store.get(HTTPS_USERS);
                            if (index % 5 == 0) {
                                store.remove(HTTPS_USERS, cookie);
                            }
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (var future : futures) {
                    future.get(6, TimeUnit.SECONDS);
                }
            }
        });

        assertThat(store.snapshot()).hasSizeLessThanOrEqualTo(ManagedCookieStore.MAX_COOKIES_PER_DOMAIN);
        assertThatCode(() -> List.copyOf(store.getCookies())).doesNotThrowAnyException();
    }

    private StoredCookie cookie(
            String name,
            String domain,
            String path,
            boolean hostOnly,
            boolean secure
    ) {
        return new StoredCookie(
                UUID.randomUUID(), name, "value", domain, path, hostOnly, secure, false,
                CookieExpiration.session(), NOW, NOW);
    }

    private List<String> names(List<StoredCookie> cookies) {
        return cookies.stream().map(StoredCookie::name).toList();
    }
}
