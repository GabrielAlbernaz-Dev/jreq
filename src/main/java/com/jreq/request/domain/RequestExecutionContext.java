package com.jreq.request.domain;

import java.util.Objects;

public record RequestExecutionContext(RequestLocation location, EnvironmentSelection environment) {
    public RequestExecutionContext {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(environment, "environment");
    }
}
