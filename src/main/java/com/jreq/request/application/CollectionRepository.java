package com.jreq.request.application;

import com.jreq.request.domain.RequestCollection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository {
    RequestCollection save(RequestCollection collection);

    Optional<RequestCollection> findById(UUID id);

    List<RequestCollection> findAll();

    void deleteById(UUID id, boolean deleteContainedRequests);
}
