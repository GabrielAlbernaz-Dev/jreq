package com.jreq.bootstrap;

import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.RequestBody;
import com.jreq.shared.json.JReqObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

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
                    .contains(
                            "app_setting", "collection", "saved_request", "request_history",
                            "environment", "environment_variable", "global_variable",
                            "environment_selection", "flyway_schema_history");
            assertThat(pragmaValue(statement, "foreign_keys")).isEqualTo("1");
            assertThat(pragmaValue(statement, "journal_mode")).isEqualToIgnoringCase("wal");
            assertThat(pragmaValue(statement, "busy_timeout")).isEqualTo("5000");
        }
    }

    @Test
    void migratesDuplicateLegacyRequestNamesWithoutDesynchronizingJson() throws Exception {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("upgrade.db"));
        Flyway.configure()
                .dataSource(factory.dataSource())
                .locations("classpath:db/migration")
                .target("1")
                .load()
                .migrate();
        var mapper = JReqObjectMapper.create();
        HttpRequestDefinition first = request("Health");
        HttpRequestDefinition second = request("Health");
        String insert = """
                INSERT INTO saved_request (id, name, method, url, definition_json)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = factory.openConnection();
             PreparedStatement statement = connection.prepareStatement(insert)) {
            for (HttpRequestDefinition request : List.of(first, second)) {
                statement.setString(1, request.id().toString());
                statement.setString(2, request.name());
                statement.setString(3, request.method().name());
                statement.setString(4, request.url());
                statement.setString(5, mapper.writeValueAsString(request));
                statement.addBatch();
            }
            statement.executeBatch();
        }

        new DatabaseInitializer(factory).initialize();

        try (Connection connection = factory.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT name, definition_json FROM saved_request ORDER BY rowid")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(mapper.readValue(resultSet.getString("definition_json"),
                    HttpRequestDefinition.class).name()).isEqualTo(resultSet.getString("name"));
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("name")).startsWith("Health [");
            assertThat(mapper.readValue(resultSet.getString("definition_json"),
                    HttpRequestDefinition.class).name()).isEqualTo(resultSet.getString("name"));
        }
    }

    private HttpRequestDefinition request(String name) {
        return new HttpRequestDefinition(
                UUID.randomUUID(), name, HttpMethod.GET, "https://example.com/health",
                List.of(), List.of(), RequestBody.none());
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
