package com.jreq.request.application;

import com.jreq.shared.validation.Constraints;

import java.time.Duration;

public record HttpTimeout(Duration value) {
    public HttpTimeout {
        value = Constraints.positive(value, "value", "HTTP timeout must be positive");
    }

    public static HttpTimeout of(Duration value) {
        return new HttpTimeout(value);
    }

    public static HttpTimeout ofSeconds(long seconds) {
        return of(Duration.ofSeconds(seconds));
    }
}
