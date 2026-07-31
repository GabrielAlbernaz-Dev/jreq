package com.jreq.request.domain;

import com.jreq.shared.validation.Constraints;

import java.util.Locale;

public final class WorkspaceName {
    private WorkspaceName() {
    }

    public static String require(String value) {
        return Constraints.requiredText(value, "name", "Name is required.");
    }

    public static String comparisonKey(String value) {
        return require(value).toLowerCase(Locale.ROOT);
    }
}
