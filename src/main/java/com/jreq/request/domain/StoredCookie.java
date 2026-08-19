package com.jreq.request.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record StoredCookie(
        UUID id,
        String name,
        String value,
        String domain,
        String path,
        boolean hostOnly,
        boolean secure,
        boolean httpOnly,
        CookieExpiration expiration,
        Instant createdAt,
        Instant updatedAt
) {
    private static final Pattern COOKIE_NAME = Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");

    public StoredCookie {
        Objects.requireNonNull(id, "id");
        name = validateName(name);
        value = validateSafeText(value, "Cookie value contains unsafe characters");
        if (value.contains(";")) {
            throw new IllegalArgumentException("Cookie value is invalid");
        }
        domain = normalizeDomain(domain);
        path = normalizePath(path);
        Objects.requireNonNull(expiration, "expiration");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (name.startsWith("__Secure-") && !secure) {
            throw new IllegalArgumentException("__Secure- cookies require the Secure attribute");
        }
        if (name.startsWith("__Host-") && (!secure || !hostOnly || !"/".equals(path))) {
            throw new IllegalArgumentException("__Host- cookies require Secure, host-only, and / path");
        }
    }

    public CookieIdentity identity() {
        return new CookieIdentity(name, domain, path);
    }

    public boolean isSession() {
        return expiration instanceof CookieExpiration.Session;
    }

    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return expiration instanceof CookieExpiration.At at && !at.instant().isAfter(instant);
    }

    private static String validateName(String candidate) {
        String value = Objects.requireNonNull(candidate, "name");
        if (!COOKIE_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Cookie name is invalid");
        }
        return value;
    }

    private static String normalizeDomain(String candidate) {
        String value = validateSafeText(candidate, "Cookie domain contains unsafe characters")
                .strip().toLowerCase(Locale.ROOT);
        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        if (value.isBlank() || value.contains("/") || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Cookie domain is invalid");
        }
        return value;
    }

    private static String normalizePath(String candidate) {
        String value = validateSafeText(candidate, "Cookie path contains unsafe characters");
        if (!value.startsWith("/")) {
            throw new IllegalArgumentException("Cookie path must start with /");
        }
        return value;
    }

    private static String validateSafeText(String candidate, String message) {
        String value = Objects.requireNonNull(candidate, "cookie text");
        if (value.codePoints().anyMatch(codePoint -> codePoint == 0x7f || codePoint < 0x20)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
