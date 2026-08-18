package com.jreq.request.application;

import com.jreq.request.domain.StoredCookie;

import java.time.Instant;
import java.util.List;

public interface CookieRepository {
    List<StoredCookie> findAll(Instant now);

    void replaceAll(List<StoredCookie> cookies);
}
