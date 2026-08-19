package com.jreq.request.infrastructure.http;

import com.jreq.request.application.CookieJar;
import com.jreq.request.application.CookieJarEdit;
import com.jreq.request.application.CookieJarState;
import com.jreq.request.domain.CookieExpiration;
import com.jreq.request.domain.CookieIdentity;
import com.jreq.request.domain.StoredCookie;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class ManagedCookieStore implements CookieStore, CookieJar {
    public static final int MAX_COOKIE_BYTES = 4_096;
    public static final int MAX_COOKIES_PER_DOMAIN = 180;
    public static final int MAX_COOKIES_TOTAL = 3_000;

    private static final Comparator<StoredCookie> SEND_ORDER = Comparator
            .comparingInt((StoredCookie cookie) -> cookie.path().length()).reversed()
            .thenComparing(StoredCookie::createdAt)
            .thenComparing(cookie -> cookie.id().toString());

    private final Clock clock;
    private final Map<CookieIdentity, StoredCookie> cookies = new HashMap<>();
    private final Map<CookieIdentity, Long> insertionOrder = new HashMap<>();
    private final Map<String, Integer> domainCounts = new HashMap<>();
    private final AtomicLong orderSequence = new AtomicLong();

    private long revision;
    private long persistedRevision;

    public ManagedCookieStore() {
        this(Clock.systemUTC());
    }

    public ManagedCookieStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized void add(URI uri, HttpCookie cookie) {
        Objects.requireNonNull(cookie, "cookie");
        if (uri == null || uri.getHost() == null || cookieSize(cookie) > MAX_COOKIE_BYTES) {
            return;
        }
        Instant now = clock.instant();
        String originHost = normalizeHost(uri.getHost());
        boolean hostOnly = cookie.getDomain() == null || cookie.getDomain().isBlank();
        String domain = hostOnly ? originHost : normalizeDomain(cookie.getDomain());
        if (!domainMatches(originHost, domain, hostOnly)) {
            return;
        }
        String path = cookie.getPath() == null || !cookie.getPath().startsWith("/")
                ? defaultPath(uri.getPath())
                : cookie.getPath();
        CookieIdentity identity = new CookieIdentity(cookie.getName(), domain, path);
        // Max-Age=0 is the RFC deletion signal when callers use HttpCookie directly.
        // Response capture uses acceptParsed() so broken Expires parsing cannot delete.
        if (cookie.getMaxAge() == 0) {
            if (removeCookie(identity)) {
                revision++;
            }
            return;
        }

        CookieExpiration expiration = expiration(cookie.getMaxAge(), now);
        store(identity, cookie.getName(), cookie.getValue(), domain, path, hostOnly,
                cookie.getSecure(), cookie.isHttpOnly(), expiration, now);
    }

    public synchronized void acceptParsed(URI uri, CookieHeaderParser.ParsedCookie parsed) {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(parsed, "parsed");
        if (uri.getHost() == null) {
            return;
        }
        Instant now = clock.instant();
        String originHost = normalizeHost(uri.getHost());
        if (!domainMatches(originHost, parsed.domain(), parsed.hostOnly())) {
            return;
        }
        if (!policyAllows(uri, parsed)) {
            return;
        }
        int size = (parsed.name() + "=" + parsed.value()).getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_COOKIE_BYTES) {
            return;
        }
        CookieIdentity identity = new CookieIdentity(parsed.name(), parsed.domain(), parsed.path());
        if (parsed.isDeletion()) {
            if (removeCookie(identity)) {
                revision++;
            }
            return;
        }
        CookieExpiration expiration = parsed.toCookieExpiration().orElse(null);
        if (expiration == null) {
            return;
        }
        store(identity, parsed.name(), parsed.value(), parsed.domain(), parsed.path(),
                parsed.hostOnly(), parsed.secure(), parsed.httpOnly(), expiration, now);
    }

    @Override
    public synchronized List<HttpCookie> get(URI uri) {
        return matching(uri).stream().map(this::toHttpCookie).toList();
    }

    @Override
    public synchronized List<HttpCookie> getCookies() {
        return snapshot().stream().map(this::toHttpCookie).toList();
    }

    @Override
    public synchronized List<URI> getURIs() {
        return snapshot().stream()
                .map(cookie -> URI.create((cookie.secure() ? "https://" : "http://") + cookie.domain()))
                .distinct()
                .toList();
    }

    @Override
    public synchronized boolean remove(URI uri, HttpCookie cookie) {
        Objects.requireNonNull(cookie, "cookie");
        if (uri == null || uri.getHost() == null) {
            return false;
        }
        String domain = cookie.getDomain() == null || cookie.getDomain().isBlank()
                ? normalizeHost(uri.getHost())
                : normalizeDomain(cookie.getDomain());
        String path = cookie.getPath() == null || !cookie.getPath().startsWith("/")
                ? defaultPath(uri.getPath())
                : cookie.getPath();
        CookieIdentity identity = new CookieIdentity(cookie.getName(), domain, path);
        if (!removeCookie(identity)) {
            return false;
        }
        revision++;
        return true;
    }

    @Override
    public synchronized boolean removeAll() {
        if (cookies.isEmpty()) {
            return false;
        }
        cookies.clear();
        insertionOrder.clear();
        domainCounts.clear();
        revision++;
        return true;
    }

    @Override
    public synchronized List<StoredCookie> snapshot() {
        purgeExpired();
        return cookies.values().stream()
                .sorted(Comparator.comparing(StoredCookie::domain)
                        .thenComparing(StoredCookie::path)
                        .thenComparing(StoredCookie::name))
                .toList();
    }

    @Override
    public synchronized List<StoredCookie> matching(URI uri) {
        Objects.requireNonNull(uri, "uri");
        purgeExpired();
        if (uri.getHost() == null) {
            return List.of();
        }
        String host = normalizeHost(uri.getHost());
        String requestPath = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        boolean secureRequest = "https".equalsIgnoreCase(uri.getScheme());
        return cookies.values().stream()
                .filter(cookie -> domainMatches(host, cookie.domain(), cookie.hostOnly()))
                .filter(cookie -> pathMatches(requestPath, cookie.path()))
                .filter(cookie -> !cookie.secure() || secureRequest)
                .sorted(SEND_ORDER)
                .toList();
    }

    @Override
    public synchronized CookieJarState state() {
        List<StoredCookie> current = snapshot();
        return new CookieJarState(revision, persistedRevision, current);
    }

    @Override
    public synchronized void restore(List<StoredCookie> restoredCookies) {
        Objects.requireNonNull(restoredCookies, "restoredCookies");
        cookies.clear();
        insertionOrder.clear();
        domainCounts.clear();
        Instant now = clock.instant();
        restoredCookies.stream()
                .filter(cookie -> !cookie.isExpiredAt(now))
                .sorted(Comparator.comparing(StoredCookie::createdAt))
                .forEach(cookie -> {
                    if (cookies.put(cookie.identity(), cookie) == null) {
                        incrementDomain(cookie.domain());
                    }
                    insertionOrder.put(cookie.identity(), orderSequence.incrementAndGet());
                    enforceLimits(cookie.domain());
                });
        revision = 0;
        persistedRevision = 0;
    }

    @Override
    public synchronized void replaceAll(List<StoredCookie> replacement) {
        Objects.requireNonNull(replacement, "replacement");
        cookies.clear();
        insertionOrder.clear();
        domainCounts.clear();
        Instant now = clock.instant();
        replacement.stream()
                .filter(cookie -> !cookie.isExpiredAt(now))
                .forEach(cookie -> {
                    if (cookies.put(cookie.identity(), cookie) == null) {
                        incrementDomain(cookie.domain());
                    }
                    insertionOrder.put(cookie.identity(), orderSequence.incrementAndGet());
                    enforceLimits(cookie.domain());
                });
        revision++;
    }

    @Override
    public synchronized void applyEdit(CookieJarEdit edit) {
        Objects.requireNonNull(edit, "edit");
        if (edit.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        boolean changed = false;
        if (edit.clearAll()) {
            if (!cookies.isEmpty() || !edit.upserts().isEmpty()) {
                cookies.clear();
                insertionOrder.clear();
                domainCounts.clear();
                changed = true;
            }
            for (StoredCookie cookie : edit.upserts()) {
                if (cookie.isExpiredAt(now)) {
                    continue;
                }
                if (cookies.put(cookie.identity(), cookie) == null) {
                    incrementDomain(cookie.domain());
                }
                insertionOrder.put(cookie.identity(), orderSequence.incrementAndGet());
                enforceLimits(cookie.domain());
                changed = true;
            }
        } else {
            for (CookieIdentity identity : edit.deletions()) {
                if (removeCookie(identity)) {
                    changed = true;
                }
            }
            for (StoredCookie cookie : edit.upserts()) {
                if (cookie.isExpiredAt(now)) {
                    if (removeCookie(cookie.identity())) {
                        changed = true;
                    }
                    continue;
                }
                StoredCookie previous = cookies.put(cookie.identity(), cookie);
                if (previous == null) {
                    incrementDomain(cookie.domain());
                } else if (!previous.domain().equals(cookie.domain())) {
                    domainCounts.computeIfPresent(previous.domain(),
                            (domain, count) -> count == 1 ? null : count - 1);
                    incrementDomain(cookie.domain());
                }
                insertionOrder.put(cookie.identity(), orderSequence.incrementAndGet());
                enforceLimits(cookie.domain());
                changed = true;
            }
        }
        if (changed) {
            revision++;
        }
    }

    @Override
    public synchronized void markPersisted(long persisted) {
        if (persisted < 0 || persisted > revision) {
            throw new IllegalArgumentException("Persisted cookie revision is invalid");
        }
        persistedRevision = Math.max(persistedRevision, persisted);
    }

    private void store(
            CookieIdentity identity,
            String name,
            String value,
            String domain,
            String path,
            boolean hostOnly,
            boolean secure,
            boolean httpOnly,
            CookieExpiration expiration,
            Instant now
    ) {
        StoredCookie existing = cookies.get(identity);
        try {
            StoredCookie stored = new StoredCookie(
                    existing == null ? UUID.randomUUID() : existing.id(),
                    name, value, domain, path, hostOnly, secure, httpOnly, expiration,
                    existing == null ? now : existing.createdAt(), now);
            if (cookies.put(identity, stored) == null) {
                incrementDomain(domain);
            }
            insertionOrder.put(identity, orderSequence.incrementAndGet());
            revision++;
            enforceLimits(domain);
        } catch (IllegalArgumentException ignoredUnsafeCookie) {
            // Invalid response cookies are ignored without exposing their values.
        }
    }

    private static boolean policyAllows(URI uri, CookieHeaderParser.ParsedCookie parsed) {
        boolean hostOnly = parsed.hostOnly();
        String domain = parsed.domain();
        if (!domainMatches(uri.getHost(), domain, hostOnly)) {
            return false;
        }
        boolean exactHost = uri.getHost().equalsIgnoreCase(domain);
        if (!hostOnly && !exactHost
                && (!domain.contains(".") || isIpAddress(uri.getHost()))) {
            return false;
        }
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        if (parsed.name().startsWith("__Secure-") && (!https || !parsed.secure())) {
            return false;
        }
        if (parsed.name().startsWith("__Host-")
                && (!https || !parsed.secure() || !hostOnly || !"/".equals(parsed.path()))) {
            return false;
        }
        return true;
    }

    private static boolean isIpAddress(String host) {
        return host.indexOf(':') >= 0 || host.chars().allMatch(character ->
                Character.isDigit(character) || character == '.');
    }

    static boolean domainMatches(String requestHost, String cookieDomain, boolean hostOnly) {
        String host = normalizeHost(requestHost);
        String domain = normalizeDomain(cookieDomain);
        return hostOnly ? host.equals(domain) : host.equals(domain) || host.endsWith("." + domain);
    }

    static boolean pathMatches(String requestPath, String cookiePath) {
        if (requestPath.equals(cookiePath)) {
            return true;
        }
        if (!requestPath.startsWith(cookiePath)) {
            return false;
        }
        return cookiePath.endsWith("/")
                || (requestPath.length() > cookiePath.length()
                && requestPath.charAt(cookiePath.length()) == '/');
    }

    private CookieExpiration expiration(long maxAge, Instant now) {
        if (maxAge < 0) {
            return CookieExpiration.session();
        }
        try {
            return CookieExpiration.at(now.plusSeconds(maxAge));
        } catch (DateTimeException | ArithmeticException overflow) {
            return CookieExpiration.at(Instant.MAX);
        }
    }

    private HttpCookie toHttpCookie(StoredCookie stored) {
        HttpCookie cookie = new HttpCookie(stored.name(), stored.value());
        if (!stored.hostOnly()) {
            cookie.setDomain(stored.domain());
        }
        cookie.setPath(stored.path());
        cookie.setSecure(stored.secure());
        cookie.setHttpOnly(stored.httpOnly());
        if (stored.expiration() instanceof CookieExpiration.At at) {
            cookie.setMaxAge(Math.max(0, at.instant().getEpochSecond() - clock.instant().getEpochSecond()));
        }
        cookie.setVersion(0);
        return cookie;
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        List<CookieIdentity> expired = cookies.entrySet().stream()
                .filter(entry -> entry.getValue().isExpiredAt(now))
                .map(Map.Entry::getKey)
                .toList();
        if (expired.isEmpty()) {
            return;
        }
        expired.forEach(identity -> {
            removeCookie(identity);
        });
        revision++;
    }

    private void enforceLimits(String changedDomain) {
        if (domainCounts.getOrDefault(changedDomain, 0) > MAX_COOKIES_PER_DOMAIN) {
            evictOldest(cookies.values().stream()
                    .filter(cookie -> cookie.domain().equals(changedDomain))
                    .toList(), MAX_COOKIES_PER_DOMAIN);
        }
        if (cookies.size() > MAX_COOKIES_TOTAL) {
            evictOldest(new ArrayList<>(cookies.values()), MAX_COOKIES_TOTAL);
        }
    }

    private void evictOldest(List<StoredCookie> candidates, int maximum) {
        int excess = candidates.size() - maximum;
        if (excess <= 0) {
            return;
        }
        for (int index = 0; index < excess; index++) {
            candidates.stream()
                    .filter(cookie -> cookies.containsKey(cookie.identity()))
                    .min(Comparator.comparingLong(cookie ->
                            insertionOrder.getOrDefault(cookie.identity(), 0L)))
                    .ifPresent(cookie -> removeCookie(cookie.identity()));
        }
    }

    private boolean removeCookie(CookieIdentity identity) {
        StoredCookie removed = cookies.remove(identity);
        if (removed == null) {
            return false;
        }
        insertionOrder.remove(identity);
        domainCounts.computeIfPresent(removed.domain(), (domain, count) -> count == 1 ? null : count - 1);
        return true;
    }

    private void incrementDomain(String domain) {
        domainCounts.merge(domain, 1, Integer::sum);
    }

    private int cookieSize(HttpCookie cookie) {
        return (cookie.getName() + "=" + cookie.getValue()).getBytes(StandardCharsets.UTF_8).length;
    }

    private static String defaultPath(String requestPath) {
        if (requestPath == null || requestPath.isEmpty() || !requestPath.startsWith("/")) {
            return "/";
        }
        int lastSlash = requestPath.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : requestPath.substring(0, lastSlash);
    }

    private static String normalizeHost(String host) {
        return Objects.requireNonNull(host, "host").toLowerCase(Locale.ROOT);
    }

    private static String normalizeDomain(String domain) {
        String normalized = Objects.requireNonNull(domain, "domain").toLowerCase(Locale.ROOT);
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
