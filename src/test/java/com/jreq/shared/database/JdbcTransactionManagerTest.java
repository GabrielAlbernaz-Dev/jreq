package com.jreq.shared.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcTransactionManagerTest {
    @TempDir
    Path temporaryDirectory;

    private SqliteConnectionFactory connectionFactory;
    private JdbcTransactionManager transactionManager;

    @BeforeEach
    void setUp() throws SQLException {
        connectionFactory = new SqliteConnectionFactory(temporaryDirectory.resolve("transactions.db"));
        transactionManager = new JdbcTransactionManager(connectionFactory);
        try (Connection connection = connectionFactory.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE transaction_test (value TEXT NOT NULL)");
        }
    }

    @Test
    void commitsSuccessfulWorkAndReturnsItsResult() throws SQLException {
        String result = transactionManager.execute(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO transaction_test (value) VALUES ('committed')");
            }
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(storedValues()).isEqualTo(1);
    }

    @Test
    void rollsBackFailedWork() {
        assertThatThrownBy(() -> transactionManager.run(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO transaction_test (value) VALUES ('rolled back')");
            }
            throw new IllegalStateException("transaction failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(storedValues()).isZero();
    }

    @Test
    void rollsBackAndPropagatesCheckedFailures() {
        assertThatThrownBy(() -> transactionManager.run(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO transaction_test (value) VALUES ('checked failure')");
            }
            throw new IOException("checked transaction failure");
        })).isInstanceOf(IOException.class)
                .hasMessage("checked transaction failure");

        assertThat(storedValues()).isZero();
    }

    private int storedValues() {
        try (Connection connection = connectionFactory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM transaction_test")) {
            return resultSet.getInt(1);
        } catch (SQLException exception) {
            throw new AssertionError(exception);
        }
    }
}
