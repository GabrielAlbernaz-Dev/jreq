package com.jreq.request.application;

import com.jreq.request.domain.EnvironmentSelection;
import com.jreq.request.domain.RequestLocation;

import java.util.Objects;

public record EnvironmentActivation(RequestLocation location, EnvironmentSelection selection) {
    public EnvironmentActivation {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(selection, "selection");
    }
}
