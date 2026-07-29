package com.jreq.request.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

final class JdbcCommands {
    private JdbcCommands() {
    }

    static int executeUpdate(Connection connection, String sql, StatementBinder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        }
    }

    static int executeUpdate(Connection connection, String sql) throws SQLException {
        return executeUpdate(connection, sql, statement -> {
        });
    }

    @FunctionalInterface
    interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
