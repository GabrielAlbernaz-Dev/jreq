package com.jreq.request.application;

import com.jreq.request.domain.CookieIdentity;
import com.jreq.request.domain.StoredCookie;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CookieJarEdit(
        List<StoredCookie> upserts,
        Set<CookieIdentity> deletions,
        boolean clearAll
) {
    public CookieJarEdit {
        upserts = List.copyOf(Objects.requireNonNull(upserts, "upserts"));
        deletions = Set.copyOf(Objects.requireNonNull(deletions, "deletions"));
    }

    public static CookieJarEdit of(
            List<StoredCookie> upserts,
            Set<CookieIdentity> deletions,
            boolean clearAll
    ) {
        return new CookieJarEdit(upserts, deletions, clearAll);
    }

    public boolean isEmpty() {
        return !clearAll && upserts.isEmpty() && deletions.isEmpty();
    }
}
