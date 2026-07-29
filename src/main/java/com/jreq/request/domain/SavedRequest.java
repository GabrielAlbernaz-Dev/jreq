package com.jreq.request.domain;

import java.time.Instant;
import java.util.Objects;

public record SavedRequest(
        HttpRequestDefinition definition,
        RequestLocation location,
        Instant createdAt,
        Instant updatedAt
) {
    public SavedRequest {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
