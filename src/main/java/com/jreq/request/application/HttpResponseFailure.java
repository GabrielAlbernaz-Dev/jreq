package com.jreq.request.application;

import com.jreq.shared.exception.ErrorCategory;

import java.time.Duration;
import java.util.Objects;

public record HttpResponseFailure(
        ErrorCategory category,
        String userMessage,
        Duration duration
) implements HttpResponseResult {
    public HttpResponseFailure {
        Objects.requireNonNull(category, "category");
        userMessage = Objects.requireNonNull(userMessage, "userMessage");
        Objects.requireNonNull(duration, "duration");
    }
}
