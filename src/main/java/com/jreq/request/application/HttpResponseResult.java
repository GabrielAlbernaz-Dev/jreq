package com.jreq.request.application;

import java.time.Duration;

public sealed interface HttpResponseResult permits HttpResponseSuccess, HttpResponseFailure {
    Duration duration();

    default boolean isSuccess() {
        return this instanceof HttpResponseSuccess;
    }
}
