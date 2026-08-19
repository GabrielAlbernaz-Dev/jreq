package com.jreq.request.domain;

import java.util.Objects;

public record CookieIdentity(String name, String domain, String path) {
    public CookieIdentity {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(path, "path");
    }
}
