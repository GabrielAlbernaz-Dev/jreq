package com.jreq.request.infrastructure.http;

import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;

public final class JReqCookiePolicy implements CookiePolicy {
    @Override
    public boolean shouldAccept(URI uri, HttpCookie cookie) {
        if (uri == null || uri.getHost() == null || cookie == null) {
            return false;
        }
        boolean hostOnly = cookie.getDomain() == null || cookie.getDomain().isBlank();
        String domain = hostOnly ? uri.getHost() : cookie.getDomain();
        if (!ManagedCookieStore.domainMatches(uri.getHost(), domain, hostOnly)) {
            return false;
        }
        String normalizedDomain = domain.startsWith(".") ? domain.substring(1) : domain;
        boolean exactHost = uri.getHost().equalsIgnoreCase(normalizedDomain);
        if (!hostOnly && !exactHost
                && (!normalizedDomain.contains(".") || isIpAddress(uri.getHost()))) {
            return false;
        }
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        if (cookie.getName().startsWith("__Secure-") && (!https || !cookie.getSecure())) {
            return false;
        }
        if (cookie.getName().startsWith("__Host-")
                && (!https || !cookie.getSecure() || !hostOnly || !"/".equals(cookie.getPath()))) {
            return false;
        }
        return true;
    }

    private boolean isIpAddress(String host) {
        return host.indexOf(':') >= 0 || host.chars().allMatch(character ->
                Character.isDigit(character) || character == '.');
    }
}
