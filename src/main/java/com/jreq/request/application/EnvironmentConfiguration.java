package com.jreq.request.application;

import com.jreq.request.domain.EnvironmentVariable;
import com.jreq.request.domain.RequestEnvironment;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record EnvironmentConfiguration(
        List<EnvironmentVariable> globals,
        List<RequestEnvironment> environments
) {
    public EnvironmentConfiguration {
        globals = List.copyOf(Objects.requireNonNull(globals, "globals"));
        environments = List.copyOf(Objects.requireNonNull(environments, "environments"));
        requireUniqueGlobalKeys(globals);
    }

    public static EnvironmentConfiguration empty() {
        return new EnvironmentConfiguration(List.of(), List.of());
    }

    public Optional<RequestEnvironment> findEnvironment(UUID id) {
        Objects.requireNonNull(id, "id");
        return environments.stream().filter(environment -> environment.id().equals(id)).findFirst();
    }

    private static void requireUniqueGlobalKeys(List<EnvironmentVariable> globals) {
        Set<String> keys = new HashSet<>();
        for (EnvironmentVariable variable : globals) {
            if (!keys.add(variable.key())) {
                throw new IllegalArgumentException("Duplicate global variable key: " + variable.key());
            }
        }
    }
}
