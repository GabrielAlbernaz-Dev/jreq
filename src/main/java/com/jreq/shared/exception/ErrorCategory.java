package com.jreq.shared.exception;

public enum ErrorCategory {
    INVALID_URL,
    DNS_ERROR,
    CONNECTION_REFUSED,
    TIMEOUT,
    TLS_ERROR,
    DATABASE_ERROR,
    SERIALIZATION_ERROR,
    UNKNOWN
}
