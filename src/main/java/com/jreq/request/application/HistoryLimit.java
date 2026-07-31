package com.jreq.request.application;

import com.jreq.shared.validation.Constraints;

public record HistoryLimit(int value) {
    public HistoryLimit {
        value = Constraints.positive(value, "History limit must be positive");
    }

    public static HistoryLimit of(int value) {
        return new HistoryLimit(value);
    }
}
