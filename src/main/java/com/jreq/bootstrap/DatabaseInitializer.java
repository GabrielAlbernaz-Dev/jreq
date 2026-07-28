package com.jreq.bootstrap;

import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.exception.JReqException;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class DatabaseInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final SqliteConnectionFactory connectionFactory;

    public DatabaseInitializer(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    public void initialize() {
        try {
            Flyway.configure()
                    .dataSource(connectionFactory.dataSource())
                    .locations("classpath:db/migration")
                    .validateMigrationNaming(true)
                    .load()
                    .migrate();
            applyConnectionPragmas();
            LOGGER.info("Database migrations completed");
        } catch (RuntimeException | SQLException exception) {
            throw new JReqException(ErrorCategory.DATABASE_ERROR,
                    "Unable to initialize the local database", exception);
        }
    }

    private void applyConnectionPragmas() throws SQLException {
        try (Connection connection = connectionFactory.openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }
}
