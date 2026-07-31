package com.jreq.request.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
        variables = List.copyOf(Objects.requireNonNull(variables, "variables"));
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        requireUniqueKeys(variables);
    }

    public RequestEnvironment withNameAndVariables(String newName, List<EnvironmentVariable> newVariables) {
        return new RequestEnvironment(id, newName, scope, newVariables, createdAt, updatedAt);
    }

    private static void requireUniqueKeys(List<EnvironmentVariable> variables) {
        Set<String> keys = new HashSet<>();
        for (EnvironmentVariable variable : variables) {
            if (!keys.add(variable.key())) {
                throw new IllegalArgumentException("Duplicate variable key: " + variable.key());
            }
        }
    }
}
