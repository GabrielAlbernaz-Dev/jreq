package com.jreq.request.infrastructure.http;

import com.jreq.request.domain.CookieExpiration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class CookieHeaderParser {
    public static final int MAX_PASTE_CHARS = 256 * 1_024;
    public static final int MAX_COOKIES_PER_PASTE = 200;
    public static final int MAX_COOKIE_BYTES = ManagedCookieStore.MAX_COOKIE_BYTES;

    private static final Pattern COOKIE_NAME =
            Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");
    private static final Set<String> KNOWN_ATTRIBUTES = Set.of(
            "domain", "path", "expires", "max-age", "secure", "httponly",
            "samesite", "version", "comment", "commenturl", "discard", "port", "priority");
    private static final DateTimeFormatter[] EXPIRES_FORMATTERS = {
            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US),
            DateTimeFormatter.ofPattern("EEE, dd-MMM-yyyy HH:mm:ss zzz", Locale.US),
            DateTimeFormatter.ofPattern("EEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy", Locale.US).withZone(ZoneOffset.UTC)
    };

    public sealed interface Expiration permits Expiration.Delete, Expiration.Session, Expiration.At {
        record Delete() implements Expiration {
        }

        record Session() implements Expiration {
        }

        record At(Instant instant) implements Expiration {
            public At {
                Objects.requireNonNull(instant, "instant");
            }
        }

        static Expiration delete() {
            return new Delete();
        }

        static Expiration session() {
            return new Session();
        }

        static Expiration at(Instant instant) {
            return new At(instant);
        }
    }

    public record ParsedCookie(
            String name,
            String value,
            String domain,
            String path,
            boolean hostOnly,
            boolean secure,
            boolean httpOnly,
            Expiration expiration
    ) {
        public ParsedCookie {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(domain, "domain");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(expiration, "expiration");
        }

        public Optional<CookieExpiration> toCookieExpiration() {
            return switch (expiration) {
                case Expiration.Delete ignored -> Optional.empty();
                case Expiration.Session ignored -> Optional.of(CookieExpiration.session());
                case Expiration.At at -> Optional.of(CookieExpiration.at(at.instant()));
            };
        }

        public boolean isDeletion() {
            return expiration instanceof Expiration.Delete;
        }
    }

    public record RejectedLine(int lineNumber, String reason) {
        public RejectedLine {
            if (lineNumber < 1) {
                throw new IllegalArgumentException("lineNumber must be positive");
            }
            Objects.requireNonNull(reason, "reason");
        }
    }

    public record ParseReport(List<ParsedCookie> accepted, List<RejectedLine> rejected) {
        public ParseReport {
            accepted = List.copyOf(accepted);
            rejected = List.copyOf(rejected);
        }

        public boolean isEmpty() {
            return accepted.isEmpty();
        }
    }

    public ParseReport parsePaste(String text, URI origin, Instant now) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(now, "now");
        if (text.length() > MAX_PASTE_CHARS) {
            return new ParseReport(List.of(), List.of(new RejectedLine(1, "Paste exceeds the maximum allowed size")));
        }
        if (text.codePoints().anyMatch(codePoint ->
                codePoint != '\n' && codePoint != '\t' && (codePoint == 0x7f || codePoint < 0x20))) {
            return new ParseReport(List.of(), List.of(new RejectedLine(1, "Paste contains unsafe control characters")));
        }
        List<String> lines = text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        if (lines.isEmpty()) {
            return new ParseReport(List.of(), List.of(new RejectedLine(1, "Paste does not contain any cookies")));
        }
        boolean setCookieMode = lines.stream().anyMatch(this::looksLikeSetCookie);
        List<ParsedCookie> accepted = new ArrayList<>();
        List<RejectedLine> rejected = new ArrayList<>();
        int lineNumber = 0;
        for (String rawLine : text.lines().toList()) {
            lineNumber++;
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (accepted.size() >= MAX_COOKIES_PER_PASTE) {
                rejected.add(new RejectedLine(lineNumber, "Paste exceeds the maximum cookie count"));
                break;
            }
            try {
                if (setCookieMode) {
                    accepted.add(parseSetCookieLine(stripSetCookiePrefix(line), origin, now, true));
                } else {
                    List<ParsedCookie> pairs = parseCookieHeaderLine(line, origin, now);
                    if (accepted.size() + pairs.size() > MAX_COOKIES_PER_PASTE) {
                        rejected.add(new RejectedLine(lineNumber, "Paste exceeds the maximum cookie count"));
                        break;
                    }
                    accepted.addAll(pairs);
                }
            } catch (IllegalArgumentException invalid) {
                rejected.add(new RejectedLine(lineNumber, invalid.getMessage()));
            }
        }
        return new ParseReport(accepted, rejected);
    }

    public Optional<ParsedCookie> parseSetCookie(String header, URI origin, Instant now) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(now, "now");
        try {
            return Optional.of(parseSetCookieLine(stripSetCookiePrefix(header.strip()), origin, now, false));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private boolean looksLikeSetCookie(String line) {
        String normalized = stripSetCookiePrefix(line).toLowerCase(Locale.ROOT);
        if (line.strip().regionMatches(true, 0, "set-cookie", 0, "set-cookie".length())) {
            return true;
        }
        String[] parts = normalized.split(";");
        for (int index = 1; index < parts.length; index++) {
            String attribute = parts[index].strip();
            int separator = attribute.indexOf('=');
            String name = (separator >= 0 ? attribute.substring(0, separator) : attribute).strip();
            if (KNOWN_ATTRIBUTES.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private List<ParsedCookie> parseCookieHeaderLine(String line, URI origin, Instant now) {
        String host = requireOriginHost(origin);
        String path = defaultPath(origin.getPath());
        List<ParsedCookie> cookies = new ArrayList<>();
        for (String segment : line.split(";")) {
            String trimmed = segment.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Cookie pair is invalid");
            }
            String name = trimmed.substring(0, separator).strip();
            String value = trimmed.substring(separator + 1).strip();
            cookies.add(validated(
                    name, value, host, path, true, false, false, Expiration.session(), origin, now, true));
        }
        if (cookies.isEmpty()) {
            throw new IllegalArgumentException("Cookie header does not contain name/value pairs");
        }
        return cookies;
    }

    private ParsedCookie parseSetCookieLine(String line, URI origin, Instant now, boolean strictPaste) {
        if (line.isBlank()) {
            throw new IllegalArgumentException("Set-Cookie line is empty");
        }
        String[] parts = line.split(";", -1);
        String pair = parts[0].strip();
        int separator = pair.indexOf('=');
        if (separator <= 0) {
            throw new IllegalArgumentException("Cookie name/value pair is invalid");
        }
        String name = pair.substring(0, separator).strip();
        String value = pair.substring(separator + 1).strip();

        String domainAttribute = null;
        String path = null;
        boolean secure = false;
        boolean httpOnly = false;
        Long maxAge = null;
        Instant expires = null;
        boolean expiresPresent = false;
        boolean expiresUnparseable = false;

        for (int index = 1; index < parts.length; index++) {
            String attribute = parts[index].strip();
            if (attribute.isEmpty()) {
                continue;
            }
            int attributeSeparator = attribute.indexOf('=');
            String attributeName = (attributeSeparator >= 0
                    ? attribute.substring(0, attributeSeparator)
                    : attribute).strip().toLowerCase(Locale.ROOT);
            String attributeValue = attributeSeparator >= 0
                    ? attribute.substring(attributeSeparator + 1).strip()
                    : "";
            switch (attributeName) {
                case "domain" -> domainAttribute = attributeValue;
                case "path" -> path = attributeValue;
                case "secure" -> secure = true;
                case "httponly" -> httpOnly = true;
                case "max-age" -> maxAge = parseMaxAge(attributeValue);
                case "expires" -> {
                    expiresPresent = true;
                    Optional<Instant> parsed = parseExpires(attributeValue);
                    if (parsed.isPresent()) {
                        expires = parsed.get();
                    } else {
                        expiresUnparseable = true;
                    }
                }
                default -> {
                    // Ignore unknown attributes such as SameSite.
                }
            }
        }

        Expiration expiration = resolveExpiration(maxAge, expiresPresent, expires, expiresUnparseable, now, strictPaste);
        boolean hostOnly = domainAttribute == null || domainAttribute.isBlank();
        String domain;
        if (hostOnly) {
            domain = requireOriginHost(origin);
        } else {
            domain = normalizeDomain(domainAttribute);
        }
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            path = defaultPath(origin.getPath());
        }
        return validated(name, value, domain, path, hostOnly, secure, httpOnly, expiration, origin, now, strictPaste);
    }

    private Expiration resolveExpiration(
            Long maxAge,
            boolean expiresPresent,
            Instant expires,
            boolean expiresUnparseable,
            Instant now,
            boolean strictPaste
    ) {
        if (maxAge != null) {
            if (maxAge == 0L) {
                return Expiration.delete();
            }
            if (maxAge < 0L) {
                return Expiration.session();
            }
            try {
                return Expiration.at(now.plusSeconds(maxAge));
            } catch (DateTimeException | ArithmeticException overflow) {
                return Expiration.at(Instant.MAX);
            }
        }
        if (expiresPresent) {
            if (expiresUnparseable) {
                if (strictPaste) {
                    throw new IllegalArgumentException("Expires attribute is invalid");
                }
                throw new IllegalArgumentException("Expires attribute is invalid");
            }
            if (!expires.isAfter(now)) {
                return Expiration.delete();
            }
            return Expiration.at(expires);
        }
        return Expiration.session();
    }

    private ParsedCookie validated(
            String name,
            String value,
            String domain,
            String path,
            boolean hostOnly,
            boolean secure,
            boolean httpOnly,
            Expiration expiration,
            URI origin,
            Instant now,
            boolean strictPaste
    ) {
        if (!COOKIE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Cookie name is invalid");
        }
        if (!safeText(value) || value.contains(";")) {
            throw new IllegalArgumentException("Cookie value is invalid");
        }
        if (!safeText(domain) || domain.isBlank() || domain.contains("/")
                || domain.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Cookie domain is invalid");
        }
        if (!safeText(path) || !path.startsWith("/")) {
            throw new IllegalArgumentException("Cookie path is invalid");
        }
        int size = (name + "=" + value).getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_COOKIE_BYTES) {
            throw new IllegalArgumentException("Cookie exceeds the maximum size");
        }
        String originHost = origin.getHost();
        if (originHost != null && !ManagedCookieStore.domainMatches(originHost, domain, hostOnly)) {
            throw new IllegalArgumentException("Cookie domain does not match the current URL host");
        }
        boolean https = "https".equalsIgnoreCase(origin.getScheme());
        if (name.startsWith("__Secure-") && (!secure || (strictPaste && originHost != null && !https))) {
            throw new IllegalArgumentException("__Secure- cookies require the Secure attribute"
                    + (strictPaste ? " and an HTTPS URL" : ""));
        }
        if (name.startsWith("__Host-")) {
            if (!secure || !hostOnly || !"/".equals(path)) {
                throw new IllegalArgumentException("__Host- cookies require Secure, host-only, and / path");
            }
            if (strictPaste && originHost != null && !https) {
                throw new IllegalArgumentException("__Host- cookies require an HTTPS URL");
            }
        }
        if (!hostOnly && originHost != null) {
            String normalizedDomain = normalizeDomain(domain);
            boolean exactHost = originHost.equalsIgnoreCase(normalizedDomain);
            if (!exactHost && (!normalizedDomain.contains(".") || isIpAddress(originHost))) {
                throw new IllegalArgumentException("Cookie domain is not allowed");
            }
        }
        Objects.requireNonNull(now, "now");
        return new ParsedCookie(name, value, normalizeDomain(domain), path, hostOnly, secure, httpOnly, expiration);
    }

    private static Long parseMaxAge(String raw) {
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Max-Age attribute is invalid");
        }
    }

    private static Optional<Instant> parseExpires(String raw) {
        String value = raw.strip();
        for (DateTimeFormatter formatter : EXPIRES_FORMATTERS) {
            try {
                return Optional.of(ZonedDateTime.parse(value, formatter).toInstant());
            } catch (DateTimeParseException ignored) {
                // Try the next supported cookie date format.
            }
        }
        return Optional.empty();
    }

    private static String stripSetCookiePrefix(String line) {
        String stripped = line.strip();
        if (stripped.regionMatches(true, 0, "set-cookie:", 0, "set-cookie:".length())) {
            return stripped.substring("set-cookie:".length()).strip();
        }
        if (stripped.regionMatches(true, 0, "set-cookie2:", 0, "set-cookie2:".length())) {
            return stripped.substring("set-cookie2:".length()).strip();
        }
        return stripped;
    }

    private static String requireOriginHost(URI origin) {
        if (origin.getHost() == null || origin.getHost().isBlank()) {
            throw new IllegalArgumentException("Current URL has no host");
        }
        return origin.getHost().toLowerCase(Locale.ROOT);
    }

    private static String defaultPath(String requestPath) {
        if (requestPath == null || requestPath.isEmpty() || !requestPath.startsWith("/")) {
            return "/";
        }
        int lastSlash = requestPath.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : requestPath.substring(0, lastSlash);
    }

    private static String normalizeDomain(String domain) {
        String normalized = domain.strip().toLowerCase(Locale.ROOT);
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static boolean safeText(String value) {
        return value.codePoints().noneMatch(codePoint -> codePoint == 0x7f || codePoint < 0x20);
    }

    private static boolean isIpAddress(String host) {
        return host.indexOf(':') >= 0 || host.chars().allMatch(character ->
                Character.isDigit(character) || character == '.');
    }
}
