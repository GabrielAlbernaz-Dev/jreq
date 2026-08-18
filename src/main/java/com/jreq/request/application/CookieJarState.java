package com.jreq.request.application;

import com.jreq.request.domain.StoredCookie;

import java.util.List;
import java.util.Objects;

public record CookieJarState(long revision, long persistedRevision, List<StoredCookie> cookies) {
    public CookieJarState {
        if (revision < 0 || persistedRevision < 0 || persistedRevision > revision) {
            throw new IllegalArgumentException("Cookie jar revisions are invalid");
        }
        cookies = List.copyOf(Objects.requireNonNull(cookies, "cookies"));
    }

    public boolean dirty() {
        return revision > persistedRevision;
    }

    public List<StoredCookie> persistentCookies() {
        return cookies.stream().filter(cookie -> !cookie.isSession()).toList();
    }
}
