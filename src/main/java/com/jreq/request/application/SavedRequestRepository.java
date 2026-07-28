package com.jreq.request.application;

import com.jreq.request.domain.HttpRequestDefinition;

import java.util.Optional;
import java.util.UUID;

public interface SavedRequestRepository {
    void save(HttpRequestDefinition request);

    Optional<HttpRequestDefinition> findById(UUID id);
}
