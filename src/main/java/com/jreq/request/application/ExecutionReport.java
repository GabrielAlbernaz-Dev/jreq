package com.jreq.request.application;

import java.util.Objects;

import com.jreq.shared.validation.Constraints;

public record ExecutionReport(
        HttpResponseResult result,
        boolean historySaved,
        String warning,
        String cookieWarning
) {
    public ExecutionReport {
        Objects.requireNonNull(result, "result");
        warning = Objects.requireNonNull(warning, "warning");
        cookieWarning = Objects.requireNonNull(cookieWarning, "cookieWarning");
        Constraints.requireArgument(
                !historySaved || warning.isEmpty(),
                "A saved history result cannot have a warning");
    }

    public ExecutionReport(HttpResponseResult result, boolean historySaved, String warning) {
        this(result, historySaved, warning, "");
    }

    public static ExecutionReport saved(HttpResponseResult result) {
        return new ExecutionReport(result, true, "", "");
    }

    public static ExecutionReport withoutHistory(HttpResponseResult result) {
        return new ExecutionReport(result, false, "Response received, but history could not be saved.", "");
    }

    public static ExecutionReport completed(
            HttpResponseResult result,
            boolean historySaved,
            String warning,
            String cookieWarning
    ) {
        return new ExecutionReport(result, historySaved, warning, cookieWarning);
    }

    public String userNotice() {
        return String.join(" ", java.util.stream.Stream.of(warning, cookieWarning)
                .filter(value -> !value.isEmpty())
                .toList());
    }
}
