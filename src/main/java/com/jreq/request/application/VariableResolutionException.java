package com.jreq.request.application;

import java.util.List;
import java.util.Objects;

public final class VariableResolutionException extends IllegalArgumentException {
    private final List<String> issues;

    public VariableResolutionException(List<String> issues) {
        super("Unable to resolve request variables: " + String.join(", ", issues));
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public List<String> issues() {
        return issues;
    }
}
