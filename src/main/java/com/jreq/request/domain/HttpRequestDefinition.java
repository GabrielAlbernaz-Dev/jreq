package com.jreq.request.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record HttpRequestDefinition(
        UUID id,
        String name,
        HttpMethod method,
        String url,
        List<KeyValueEntry> queryParameters,
        List<KeyValueEntry> headers,
        RequestBody body
) {
    public HttpRequestDefinition {
        Objects.requireNonNull(id, "id");
        name = WorkspaceName.require(name);
        Objects.requireNonNull(method, "method");
        url = Objects.requireNonNull(url, "url");
        queryParameters = List.copyOf(Objects.requireNonNull(queryParameters, "queryParameters"));
        headers = List.copyOf(Objects.requireNonNull(headers, "headers"));
        Objects.requireNonNull(body, "body");
    }
}
