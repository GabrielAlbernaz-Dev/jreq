package com.jreq.shared.exception;

import java.util.Objects;

public final class JReqException extends RuntimeException {
    private final ErrorCategory category;

    public JReqException(ErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = Objects.requireNonNull(category, "category");
    }

    public ErrorCategory category() {
        return category;
    }
}
