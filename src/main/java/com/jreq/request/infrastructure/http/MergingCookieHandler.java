package com.jreq.request.infrastructure.http;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class MergingCookieHandler extends CookieHandler {
    private static final Pattern SAFE_COOKIE_NAME =
            Pattern.compile("[!#$%&'*+\\-.^_`|~0-9A-Za-z]+");
    private final CookieHandler delegate;
    private final ManagedCookieStore store;
    private final CookieHeaderParser parser = new CookieHeaderParser();

    MergingCookieHandler(CookieHandler delegate, ManagedCookieStore store) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        this.store = java.util.Objects.requireNonNull(store, "store");
    }

    @Override
    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders)
            throws IOException {
        Map<String, List<String>> automatic = delegate.get(uri, requestHeaders);
        Set<String> manualNames = cookieNames(requestHeaders);
        if (manualNames.isEmpty() || automatic.isEmpty()) {
            return automatic;
        }
        Map<String, List<String>> filtered = new LinkedHashMap<>();
        automatic.forEach((name, values) -> {
            if (!"cookie".equalsIgnoreCase(name)) {
                filtered.put(name, values);
                return;
            }
            List<String> remaining = new ArrayList<>();
            for (String value : values) {
                for (String pair : value.split(";")) {
                    String trimmed = pair.strip();
                    int separator = trimmed.indexOf('=');
                    if (separator > 0
                            && !manualNames.contains(trimmed.substring(0, separator).strip().toLowerCase(Locale.ROOT))) {
                        remaining.add(trimmed);
                    }
                }
            }
            if (!remaining.isEmpty()) {
                filtered.put(name, List.of(String.join("; ", remaining)));
            }
        });
        return Map.copyOf(filtered);
    }

    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) {
        Instant now = Instant.now();
        responseHeaders.forEach((name, values) -> {
            if (!"set-cookie".equalsIgnoreCase(name) && !"set-cookie2".equalsIgnoreCase(name)) {
                return;
            }
            for (String header : values) {
                if (!isSafeCookieHeader(header)) {
                    continue;
                }
                Optional<CookieHeaderParser.ParsedCookie> parsed = parser.parseSetCookie(header, uri, now);
                parsed.ifPresent(cookie -> store.acceptParsed(uri, cookie));
            }
        });
    }

    private boolean isSafeCookieHeader(String header) {
        if (header == null || header.isBlank()) {
            return false;
        }
        if (header.codePoints().anyMatch(codePoint -> codePoint == 0x7f || codePoint < 0x20)) {
            return false;
        }
        String body = header.strip();
        if (body.regionMatches(true, 0, "set-cookie:", 0, "set-cookie:".length())) {
            body = body.substring("set-cookie:".length()).strip();
        } else if (body.regionMatches(true, 0, "set-cookie2:", 0, "set-cookie2:".length())) {
            body = body.substring("set-cookie2:".length()).strip();
        }
        int separator = body.indexOf('=');
        if (separator <= 0) {
            return false;
        }
        String name = body.substring(0, separator).strip();
        return SAFE_COOKIE_NAME.matcher(name).matches();
    }

    private Set<String> cookieNames(Map<String, List<String>> headers) {
        Set<String> names = new LinkedHashSet<>();
        headers.forEach((header, values) -> {
            if (!"cookie".equalsIgnoreCase(header)) {
                return;
            }
            for (String value : values) {
                for (String pair : value.split(";")) {
                    int separator = pair.indexOf('=');
                    if (separator > 0) {
                        names.add(pair.substring(0, separator).strip().toLowerCase(Locale.ROOT));
                    }
                }
            }
        });
        return names;
    }
}
