package com.jreq.request.application;

import com.jreq.request.domain.RequestAuthentication;

public interface AuthenticationStrategy {
    Class<? extends RequestAuthentication> authenticationType();

    String authorizationValue(RequestAuthentication authentication);
}
