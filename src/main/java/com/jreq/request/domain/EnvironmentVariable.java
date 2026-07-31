package com.jreq.request.domain;

import com.jreq.shared.validation.Constraints;

import java.util.Objects;
import java.util.UUID;

public record EnvironmentVariable(
        UUID id,
        String key,
        String value,
        boolean enabled,
        boolean secret,
        int displayOrder
) {
    public EnvironmentVariable {
        Objects.requireNonNull(id, "id");
        key = Constraints.requiredText(
                key, "key", "Variable key must not be blank");
        value = Objects.requireNonNull(value, "value");
        displayOrder = Constraints.nonNegative(
                displayOrder, "Variable display order must not be negative");
    }

    public static EnvironmentVariable create(String key, String value, int displayOrder) {
        return new EnvironmentVariable(UUID.randomUUID(), key, value, true, false, displayOrder);
    }
}
