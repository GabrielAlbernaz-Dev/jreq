package com.jreq.request.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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
        RequestBody body,
        RequestAuthentication authentication
) {
    public HttpRequestDefinition {
        Objects.requireNonNull(id, "id");
        name = WorkspaceName.require(name);
        Objects.requireNonNull(method, "method");
        url = Objects.requireNonNull(url, "url");
        queryParameters = List.copyOf(Objects.requireNonNull(queryParameters, "queryParameters"));
        headers = List.copyOf(Objects.requireNonNull(headers, "headers"));
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(authentication, "authentication");
    }

    public HttpRequestDefinition(
            UUID id,
            String name,
            HttpMethod method,
            String url,
            List<KeyValueEntry> queryParameters,
            List<KeyValueEntry> headers,
            RequestBody body
    ) {
        this(id, name, method, url, queryParameters, headers, body, RequestAuthentication.none());
    }

    @JsonCreator
    static HttpRequestDefinition fromJson(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("method") HttpMethod method,
            @JsonProperty("url") String url,
            @JsonProperty("queryParameters") List<KeyValueEntry> queryParameters,
            @JsonProperty("headers") List<KeyValueEntry> headers,
            @JsonProperty("body") RequestBody body,
            @JsonProperty("authentication") RequestAuthentication authentication
    ) {
        RequestAuthentication compatibleAuthentication = authentication == null
                ? RequestAuthentication.none()
                : authentication;
        return new HttpRequestDefinition(
                id, name, method, url, queryParameters, headers, body, compatibleAuthentication);
    }
}
