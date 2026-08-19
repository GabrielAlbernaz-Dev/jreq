package com.jreq.request.application;

import com.jreq.request.domain.StoredCookie;

import java.net.URI;
import java.util.List;

public interface CookieJar {
    List<StoredCookie> snapshot();

    List<StoredCookie> matching(URI uri);

    CookieJarState state();

    void restore(List<StoredCookie> cookies);

    void replaceAll(List<StoredCookie> cookies);

    void applyEdit(CookieJarEdit edit);

    void markPersisted(long revision);
}
