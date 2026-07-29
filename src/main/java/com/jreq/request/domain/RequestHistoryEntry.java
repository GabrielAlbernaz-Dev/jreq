package com.jreq.request.domain;

import com.jreq.request.application.HttpResponseResult;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RequestHistoryEntry(
        UUID id,
        String name,
        HttpRequestDefinition request,
        HttpResponseResult result,
        Instant createdAt
) {
    public RequestHistoryEntry {
        Objects.requireNonNull(id, "id");
        name = WorkspaceName.require(name);
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
