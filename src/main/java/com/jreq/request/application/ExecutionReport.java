package com.jreq.request.application;

import java.util.Objects;

public record ExecutionReport(HttpResponseResult result, boolean historySaved, String warning) {
    public ExecutionReport {
        Objects.requireNonNull(result, "result");
        warning = Objects.requireNonNull(warning, "warning");
        if (historySaved && !warning.isEmpty()) {
            throw new IllegalArgumentException("A saved history result cannot have a warning");
        }
    }

    public static ExecutionReport saved(HttpResponseResult result) {
        return new ExecutionReport(result, true, "");
    }

    public static ExecutionReport withoutHistory(HttpResponseResult result) {
        return new ExecutionReport(result, false, "Response received, but history could not be saved.");
    }
}
