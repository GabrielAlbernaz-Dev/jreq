package com.jreq.request.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.exception.JReqException;

import java.sql.SQLException;

final class PersistenceExceptionMapper {
    private static final int SQLITE_CONSTRAINT = 19;
    private static final String UNIQUE_CONSTRAINT = "UNIQUE constraint failed";

    private PersistenceExceptionMapper() {
    }

    static JReqException uniqueOrDatabase(
            SQLException exception,
            String databaseMessage,
            String uniqueConstraintMessage
    ) {
        if (isUniqueConstraintViolation(exception)) {
            return new JReqException(ErrorCategory.VALIDATION_ERROR, uniqueConstraintMessage, exception);
        }
        return database(exception, databaseMessage);
    }

    static JReqException database(SQLException exception, String message) {
        return new JReqException(ErrorCategory.DATABASE_ERROR, message, exception);
    }

    static JReqException serialization(JsonProcessingException exception, String message) {
        return new JReqException(ErrorCategory.SERIALIZATION_ERROR, message, exception);
    }

    private static boolean isUniqueConstraintViolation(SQLException exception) {
        return exception.getErrorCode() == SQLITE_CONSTRAINT
                && exception.getMessage() != null
                && exception.getMessage().contains(UNIQUE_CONSTRAINT);
    }
}
