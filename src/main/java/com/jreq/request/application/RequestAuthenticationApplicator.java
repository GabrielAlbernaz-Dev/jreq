package com.jreq.request.application;

import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestAuthentication;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RequestAuthenticationApplicator {
    private static final String AUTHORIZATION = "Authorization";

    private final Map<Class<? extends RequestAuthentication>, AuthenticationStrategy> strategies;

    public RequestAuthenticationApplicator(List<AuthenticationStrategy> strategies) {
        Objects.requireNonNull(strategies, "strategies");
        Map<Class<? extends RequestAuthentication>, AuthenticationStrategy> indexed = new LinkedHashMap<>();
        for (AuthenticationStrategy strategy : strategies) {
            AuthenticationStrategy value = Objects.requireNonNull(strategy, "strategy");
            AuthenticationStrategy duplicate = indexed.put(value.authenticationType(), value);
            if (duplicate != null) {
                throw new IllegalArgumentException(
                        "Duplicate authentication strategy: " + value.authenticationType().getSimpleName());
            }
        }
        this.strategies = Map.copyOf(indexed);
    }

    public HttpRequestDefinition apply(HttpRequestDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        RequestAuthentication authentication = definition.authentication();
        if (authentication instanceof RequestAuthentication.None) {
            return definition;
        }

        AuthenticationStrategy strategy = strategies.get(authentication.getClass());
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "No strategy is registered for " + authentication.getClass().getSimpleName() + ".");
        }
        String authorizationValue = strategy.authorizationValue(authentication);
        List<KeyValueEntry> headers = new java.util.ArrayList<>(definition.headers().stream()
                .filter(entry -> !entry.key().equalsIgnoreCase(AUTHORIZATION))
                .toList());
        headers.add(new KeyValueEntry(UUID.randomUUID(), AUTHORIZATION, authorizationValue, true));
        return new HttpRequestDefinition(
                definition.id(),
                definition.name(),
                definition.method(),
                definition.url(),
                definition.queryParameters(),
                headers,
                definition.body(),
                authentication);
    }
}
