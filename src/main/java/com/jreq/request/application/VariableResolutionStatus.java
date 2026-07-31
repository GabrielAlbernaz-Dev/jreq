package com.jreq.request.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record VariableResolutionStatus(
        int referenceCount,
        List<String> issues,
        Set<String> invalidReferences
) {
    public VariableResolutionStatus {
        if (referenceCount < 0) {
            throw new IllegalArgumentException("referenceCount must not be negative");
        }
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        invalidReferences = Set.copyOf(Objects.requireNonNull(invalidReferences, "invalidReferences"));
    }

    public boolean hasReferences() {
        return referenceCount > 0;
    }

    public boolean isResolved() {
        return issues.isEmpty();
    }

    public boolean isReferenceResolved(String key) {
        return !invalidReferences.contains(Objects.requireNonNull(key, "key"));
    }
}
