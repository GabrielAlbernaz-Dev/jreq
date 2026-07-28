package com.jreq.bootstrap;

import com.jreq.shared.database.SqliteConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseInitializerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void runsMigrationsAndAppliesRequiredPragmas() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("migrations.db"));

        new DatabaseInitializer(factory).initialize();

        try (Connection connection = factory.openConnection();
             Statement statement = connection.createStatement()) {
            assertThat(tableNames(statement))
                    .contains("app_setting", "collection", "saved_request", "request_history", "flyway_schema_history");
            assertThat(pragmaValue(statement, "foreign_keys")).isEqualTo("1");
            assertThat(pragmaValue(statement, "journal_mode")).isEqualToIgnoringCase("wal");
            assertThat(pragmaValue(statement, "busy_timeout")).isEqualTo("5000");
        }
    }

    private java.util.List<String> tableNames(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")) {
            java.util.List<String> names = new java.util.ArrayList<>();
            while (resultSet.next()) {
                names.add(resultSet.getString(1));
            }
            return names;
        }
    }

    private String pragmaValue(Statement statement, String pragma) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA " + pragma)) {
            return resultSet.next() ? resultSet.getString(1) : "";
        }
    }
}
