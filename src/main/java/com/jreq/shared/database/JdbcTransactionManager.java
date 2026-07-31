package com.jreq.shared.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class JdbcTransactionManager {
    private final SqliteConnectionFactory connectionFactory;

    public JdbcTransactionManager(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    public <E extends Exception> void run(TransactionWork<E> work) throws SQLException, E {
        Objects.requireNonNull(work, "work");
        execute(connection -> {
            work.execute(connection);
            return null;
        });
    }

    public <T, E extends Exception> T execute(TransactionCallback<T, E> callback) throws SQLException, E {
        Objects.requireNonNull(callback, "callback");
        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try (TransactionScope transaction = new TransactionScope(connection)) {
                T result = callback.execute(connection);
                transaction.commit();
                return result;
            }
        }
    }

    private static final class TransactionScope implements AutoCloseable {
        private final Connection connection;
        private boolean committed;

        private TransactionScope(Connection connection) {
            this.connection = connection;
        }

        private void commit() throws SQLException {
            connection.commit();
            committed = true;
        }

        @Override
        public void close() throws SQLException {
            if (!committed) {
                connection.rollback();
            }
        }
    }

    @FunctionalInterface
    public interface TransactionWork<E extends Exception> {
        void execute(Connection connection) throws SQLException, E;
    }

    @FunctionalInterface
    public interface TransactionCallback<T, E extends Exception> {
        T execute(Connection connection) throws SQLException, E;
    }
}
