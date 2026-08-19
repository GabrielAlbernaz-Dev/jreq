package com.jreq.request.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RequestAuthentication.None.class, name = "none"),
        @JsonSubTypes.Type(value = RequestAuthentication.Basic.class, name = "basic"),
        @JsonSubTypes.Type(value = RequestAuthentication.JwtBearer.class, name = "jwt-bearer")
})
public sealed interface RequestAuthentication
        permits RequestAuthentication.None,
        RequestAuthentication.Basic,
        RequestAuthentication.JwtBearer {

    static RequestAuthentication none() {
        return new None();
    }

    record None() implements RequestAuthentication {
    }

    record Basic(String username, String password) implements RequestAuthentication {
        public Basic {
            username = Objects.requireNonNull(username, "username");
            password = Objects.requireNonNull(password, "password");
        }
    }

    record JwtBearer(String token) implements RequestAuthentication {
        public JwtBearer {
            token = Objects.requireNonNull(token, "token");
        }
    }
}
