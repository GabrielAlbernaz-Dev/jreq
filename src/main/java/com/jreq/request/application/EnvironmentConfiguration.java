package com.jreq.request.application;

import com.jreq.request.domain.EnvironmentVariable;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.shared.validation.Constraints;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record EnvironmentConfiguration(
        List<EnvironmentVariable> globals,
        List<RequestEnvironment> environments
) {
    public EnvironmentConfiguration {
        globals = Constraints.immutableUniqueList(
                globals,
                "globals",
                EnvironmentVariable::key,
                key -> "Duplicate global variable key: " + key);
        environments = List.copyOf(Objects.requireNonNull(environments, "environments"));
    }

    public static EnvironmentConfiguration empty() {
        return new EnvironmentConfiguration(List.of(), List.of());
    }

    public Optional<RequestEnvironment> findEnvironment(UUID id) {
        Objects.requireNonNull(id, "id");
        return environments.stream().filter(environment -> environment.id().equals(id)).findFirst();
    }
}
