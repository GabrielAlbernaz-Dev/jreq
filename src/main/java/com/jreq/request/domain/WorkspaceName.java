package com.jreq.request.domain;

import java.util.Locale;
import java.util.Objects;

public final class WorkspaceName {
    private WorkspaceName() {
    }

    public static String require(String value) {
        String normalized = Objects.requireNonNull(value, "name").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Name is required.");
        }
        return normalized;
    }

    public static String comparisonKey(String value) {
        return require(value).toLowerCase(Locale.ROOT);
    }
}
