package com.jreq.request.domain;

import java.util.Objects;

public record HistoryExecutionContext(
        RequestLocation location,
        HistoryEnvironmentReference environment
) {
    public HistoryExecutionContext {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(environment, "environment");
    }

    public static HistoryExecutionContext rootWithoutEnvironment() {
        return new HistoryExecutionContext(RequestLocation.root(), HistoryEnvironmentReference.none());
    }
}
