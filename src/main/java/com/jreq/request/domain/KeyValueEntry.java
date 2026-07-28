package com.jreq.request.domain;

import java.util.Objects;
import java.util.UUID;

public record KeyValueEntry(UUID id, String key, String value, boolean enabled) {
    public KeyValueEntry {
        Objects.requireNonNull(id, "id");
        key = Objects.requireNonNull(key, "key");
        value = Objects.requireNonNull(value, "value");
    }

    public static KeyValueEntry empty() {
        return new KeyValueEntry(UUID.randomUUID(), "", "", true);
    }

    public KeyValueEntry withValues(String newKey, String newValue, boolean newEnabled) {
        return new KeyValueEntry(id, newKey, newValue, newEnabled);
    }
}
