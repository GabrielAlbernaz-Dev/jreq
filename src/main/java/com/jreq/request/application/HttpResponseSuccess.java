package com.jreq.request.application;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record HttpResponseSuccess(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body,
        Duration duration,
        long size
) implements HttpResponseResult {
    public HttpResponseSuccess {
        headers = Objects.requireNonNull(headers, "headers").entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue())
                ));
        body = Arrays.copyOf(Objects.requireNonNull(body, "body"), body.length);
        Objects.requireNonNull(duration, "duration");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }

    @Override
    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    public String bodyAsUtf8() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
