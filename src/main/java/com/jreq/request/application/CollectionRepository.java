package com.jreq.request.application;

import com.jreq.request.domain.RequestCollection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository {
    RequestCollection save(RequestCollection collection);

    /**
     * Persists an imported collection together with its requests and environments in a
     * single transaction. The collection name is made unique with a numeric suffix when
     * it collides with an existing one.
     *
     * @return the persisted collection, with the name actually claimed
     */
    RequestCollection saveImported(ImportedCollection imported);

    Optional<RequestCollection> findById(UUID id);

    List<RequestCollection> findAll();

    void deleteById(UUID id, boolean deleteContainedRequests);
}
