package com.jreq.request.presentation;

import com.jreq.request.application.CookieJarEdit;
import com.jreq.request.domain.CookieIdentity;
import com.jreq.request.domain.StoredCookie;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CookieManagerModel {
    private final List<StoredCookie> cookies = new ArrayList<>();
    private final Set<CookieIdentity> baselineIdentities = new HashSet<>();
    private final Set<CookieIdentity> deletedIdentities = new HashSet<>();
    private final Set<CookieIdentity> touchedIdentities = new HashSet<>();
    private final URI currentUri;
    private final Instant now;
    private boolean clearAll;

    public CookieManagerModel(List<StoredCookie> cookies, URI currentUri, Instant now) {
        this.currentUri = Objects.requireNonNull(currentUri, "currentUri");
        this.now = Objects.requireNonNull(now, "now");
        Objects.requireNonNull(cookies, "cookies").stream()
                .filter(cookie -> !cookie.isExpiredAt(now))
                .forEach(cookie -> {
                    this.cookies.add(cookie);
                    baselineIdentities.add(cookie.identity());
                });
    }

    public List<StoredCookie> cookies() {
        return sorted(cookies);
    }

    public List<StoredCookie> currentUrlCookies() {
        if (currentUri.getHost() == null) {
            return List.of();
        }
        String requestPath = currentUri.getPath() == null || currentUri.getPath().isEmpty()
                ? "/" : currentUri.getPath();
        boolean https = "https".equalsIgnoreCase(currentUri.getScheme());
        return sorted(cookies.stream()
                .filter(cookie -> domainMatches(currentUri.getHost(), cookie))
                .filter(cookie -> pathMatches(requestPath, cookie.path()))
                .filter(cookie -> !cookie.secure() || https)
                .toList());
    }

    public List<String> domains() {
        return cookies.stream().map(StoredCookie::domain).distinct().sorted().toList();
    }

    public List<StoredCookie> cookiesForDomain(String domain) {
        return sorted(cookies.stream().filter(cookie -> cookie.domain().equals(domain)).toList());
    }

    public void upsert(StoredCookie cookie) {
        Objects.requireNonNull(cookie, "cookie");
        cookies.removeIf(existing -> existing.identity().equals(cookie.identity()));
        deletedIdentities.remove(cookie.identity());
        touchedIdentities.add(cookie.identity());
        if (!cookie.isExpiredAt(now)) {
            cookies.add(cookie);
        }
    }

    public void remove(UUID id) {
        Objects.requireNonNull(id, "id");
        cookies.stream()
                .filter(cookie -> cookie.id().equals(id))
                .findFirst()
                .ifPresent(cookie -> {
                    cookies.removeIf(existing -> existing.id().equals(id));
                    deletedIdentities.add(cookie.identity());
                    touchedIdentities.add(cookie.identity());
                });
    }

    public void clearDomain(String domain) {
        Objects.requireNonNull(domain, "domain");
        List<StoredCookie> removed = cookies.stream()
                .filter(cookie -> cookie.domain().equals(domain))
                .toList();
        removed.forEach(cookie -> {
            deletedIdentities.add(cookie.identity());
            touchedIdentities.add(cookie.identity());
        });
        cookies.removeIf(cookie -> cookie.domain().equals(domain));
    }

    public void clearAll() {
        cookies.forEach(cookie -> {
            deletedIdentities.add(cookie.identity());
            touchedIdentities.add(cookie.identity());
        });
        cookies.clear();
        clearAll = true;
    }

    public CookieJarEdit toEdit() {
        if (clearAll) {
            return CookieJarEdit.of(List.copyOf(cookies), Set.of(), true);
        }
        List<StoredCookie> upserts = cookies.stream()
                .filter(cookie -> touchedIdentities.contains(cookie.identity())
                        || !baselineIdentities.contains(cookie.identity()))
                .toList();
        Set<CookieIdentity> deletions = new HashSet<>(deletedIdentities);
        deletions.removeIf(identity -> cookies.stream().anyMatch(cookie -> cookie.identity().equals(identity)));
        return CookieJarEdit.of(upserts, deletions, false);
    }

    public String maskedValue(StoredCookie cookie) {
        Objects.requireNonNull(cookie, "cookie");
        return "•".repeat(Math.max(6, Math.min(16, cookie.value().length())));
    }

    private List<StoredCookie> sorted(List<StoredCookie> values) {
        return values.stream().sorted(Comparator.comparing(StoredCookie::domain)
                .thenComparing(StoredCookie::path)
                .thenComparing(StoredCookie::name)).toList();
    }

    private boolean domainMatches(String host, StoredCookie cookie) {
        String normalizedHost = host.toLowerCase(java.util.Locale.ROOT);
        return cookie.hostOnly()
                ? normalizedHost.equals(cookie.domain())
                : normalizedHost.equals(cookie.domain()) || normalizedHost.endsWith("." + cookie.domain());
    }

    private boolean pathMatches(String requestPath, String cookiePath) {
        if (requestPath.equals(cookiePath)) {
            return true;
        }
        return requestPath.startsWith(cookiePath)
                && (cookiePath.endsWith("/")
                || (requestPath.length() > cookiePath.length()
                && requestPath.charAt(cookiePath.length()) == '/'));
    }
}
