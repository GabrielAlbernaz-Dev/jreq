package com.jreq.request.application;

import com.jreq.request.domain.RequestAuthentication;

public final class JwtBearerAuthenticationStrategy implements AuthenticationStrategy {
    @Override
    public Class<RequestAuthentication.JwtBearer> authenticationType() {
        return RequestAuthentication.JwtBearer.class;
    }

    @Override
    public String authorizationValue(RequestAuthentication authentication) {
        RequestAuthentication.JwtBearer jwt = requireJwt(authentication);
        String token = jwt.token().strip();
        if (token.isEmpty()) {
            throw new IllegalArgumentException("JWT Bearer authentication requires a token.");
        }
        return "Bearer " + token;
    }

    private RequestAuthentication.JwtBearer requireJwt(RequestAuthentication authentication) {
        if (authentication instanceof RequestAuthentication.JwtBearer jwt) {
            return jwt;
        }
        throw new IllegalArgumentException("Unsupported authentication configuration.");
    }
}
