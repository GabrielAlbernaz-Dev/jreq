package com.jreq.request.domain;

import com.jreq.shared.validation.Constraints;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RequestEnvironment(
        UUID id,
        String name,
        EnvironmentScope scope,
        List<EnvironmentVariable> variables,
        Instant createdAt,
        Instant updatedAt
) {
    public RequestEnvironment {
        Objects.requireNonNull(id, "id");
        name = WorkspaceName.require(name);
        Objects.requireNonNull(scope, "scope");
        variables = Constraints.immutableUniqueList(
                variables,
                "variables",
                EnvironmentVariable::key,
                key -> "Duplicate variable key: " + key);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public RequestEnvironment withNameAndVariables(String newName, List<EnvironmentVariable> newVariables) {
        return new RequestEnvironment(id, newName, scope, newVariables, createdAt, updatedAt);
    }
}
