package com.jreq.request.application;

import com.jreq.shared.validation.Constraints;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record VariableResolutionStatus(
        int referenceCount,
        List<String> issues,
        Set<String> invalidReferences
) {
    public VariableResolutionStatus {
        referenceCount = Constraints.nonNegative(
                referenceCount, "referenceCount must not be negative");
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
