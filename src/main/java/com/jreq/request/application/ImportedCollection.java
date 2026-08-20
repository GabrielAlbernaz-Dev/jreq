package com.jreq.request.application;

import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.domain.SavedRequest;

import java.util.List;
import java.util.Objects;

/**
 * The domain objects produced by translating an external collection file, ready to be
 * persisted atomically.
 */
public record ImportedCollection(
        RequestCollection collection,
        List<SavedRequest> requests,
        List<RequestEnvironment> environments
) {
    public ImportedCollection {
        Objects.requireNonNull(collection, "collection");
        requests = List.copyOf(Objects.requireNonNull(requests, "requests"));
        environments = List.copyOf(Objects.requireNonNull(environments, "environments"));
    }
}
