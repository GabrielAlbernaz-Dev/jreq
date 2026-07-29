package com.jreq.request.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RequestCollection(UUID id, String name, Instant createdAt, Instant updatedAt) {
    public RequestCollection {
        Objects.requireNonNull(id, "id");
        name = WorkspaceName.require(name);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public RequestCollection withName(String newName) {
        return new RequestCollection(id, newName, createdAt, updatedAt);
    }
}
