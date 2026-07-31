package com.jreq.request.application;

import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.validation.Constraints;

import java.time.Duration;
import java.util.Objects;

public record HttpResponseFailure(
        ErrorCategory category,
        String userMessage,
        Duration duration
) implements HttpResponseResult {
    public HttpResponseFailure {
        Objects.requireNonNull(category, "category");
        userMessage = Constraints.requiredText(
                userMessage, "userMessage", "userMessage is required");
        duration = Constraints.nonNegative(
                duration, "duration", "duration must not be negative");
    }
}
