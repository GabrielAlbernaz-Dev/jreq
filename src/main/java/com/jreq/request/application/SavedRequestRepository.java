package com.jreq.request.application;

import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.SavedRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedRequestRepository {
    SavedRequest save(HttpRequestDefinition request, RequestLocation location);

    Optional<SavedRequest> findById(UUID id);

    List<SavedRequest> findAll();

    void deleteById(UUID id);
}
