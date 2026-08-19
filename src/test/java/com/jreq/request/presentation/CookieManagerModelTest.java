package com.jreq.request.presentation;

import com.jreq.request.application.CookieJarEdit;
import com.jreq.request.domain.CookieExpiration;
import com.jreq.request.domain.CookieIdentity;
import com.jreq.request.domain.StoredCookie;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CookieManagerModelTest {
    private static final Instant NOW = Instant.parse("2026-08-03T20:00:00Z");

    @Test
    void filtersCurrentUrlAndGroupsAllCookiesByDomain() {
        StoredCookie current = cookie("session", "api.example.com", "/users", true);
        StoredCookie parent = cookie("theme", "example.com", "/", false);
        StoredCookie unrelated = cookie("other", "other.example", "/", true);
        CookieManagerModel model = new CookieManagerModel(
                List.of(current, parent, unrelated),
                URI.create("https://api.example.com/users/42"),
                NOW);

        assertThat(model.currentUrlCookies()).containsExactlyInAnyOrder(current, parent);
        assertThat(model.domains()).containsExactly("api.example.com", "example.com", "other.example");
    }

    @Test
    void addsReplacesDeletesAndClearsWithoutExposingValuesInLabels() {
        StoredCookie first = cookie("session", "example.com", "/", true);
        CookieManagerModel model = new CookieManagerModel(
                List.of(first), URI.create("https://example.com"), NOW);
        StoredCookie replacement = new StoredCookie(
                UUID.randomUUID(), first.name(), "new-sensitive-value", first.domain(), first.path(),
                first.hostOnly(), first.secure(), first.httpOnly(), first.expiration(), NOW, NOW);

        model.upsert(replacement);
        assertThat(model.cookies()).containsExactly(replacement);
        assertThat(model.maskedValue(replacement)).doesNotContain("new-sensitive-value");

        model.remove(replacement.id());
        assertThat(model.cookies()).isEmpty();

        model.upsert(first);
        model.clearAll();
        assertThat(model.cookies()).isEmpty();
    }

    @Test
    void toEditTracksOnlyTouchedCookiesAndDeletions() {
        StoredCookie keep = cookie("keep", "example.com", "/", true);
        StoredCookie drop = cookie("drop", "example.com", "/", true);
        CookieManagerModel model = new CookieManagerModel(
                List.of(keep, drop), URI.create("https://example.com"), NOW);
        StoredCookie added = cookie("new", "example.com", "/", true);

        model.remove(drop.id());
        model.upsert(added);

        CookieJarEdit edit = model.toEdit();
        assertThat(edit.clearAll()).isFalse();
        assertThat(edit.upserts()).containsExactly(added);
        assertThat(edit.deletions()).containsExactly(new CookieIdentity("drop", "example.com", "/"));
    }

    private StoredCookie cookie(String name, String domain, String path, boolean hostOnly) {
        return new StoredCookie(
                UUID.randomUUID(), name, name + "-sensitive", domain, path, hostOnly,
                false, true, CookieExpiration.session(), NOW, NOW);
    }
}
