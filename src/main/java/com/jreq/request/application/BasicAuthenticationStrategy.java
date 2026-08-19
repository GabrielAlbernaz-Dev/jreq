package com.jreq.request.application;

import com.jreq.request.domain.RequestAuthentication;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class BasicAuthenticationStrategy implements AuthenticationStrategy {
    @Override
    public Class<RequestAuthentication.Basic> authenticationType() {
        return RequestAuthentication.Basic.class;
    }

    @Override
    public String authorizationValue(RequestAuthentication authentication) {
        RequestAuthentication.Basic basic = requireBasic(authentication);
        if (basic.username().isBlank()) {
            throw new IllegalArgumentException("Basic authentication requires a username.");
        }
        if (basic.username().contains(":")) {
            throw new IllegalArgumentException("A Basic authentication username cannot contain ':'.");
        }
        String credentials = basic.username() + ":" + basic.password();
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private RequestAuthentication.Basic requireBasic(RequestAuthentication authentication) {
        if (authentication instanceof RequestAuthentication.Basic basic) {
            return basic;
        }
        throw new IllegalArgumentException("Unsupported authentication configuration.");
    }
}
