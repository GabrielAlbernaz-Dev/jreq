package com.jreq.request.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.HttpResponseFailure;
import com.jreq.request.application.HttpResponseResult;
import com.jreq.request.application.HttpResponseSuccess;
import com.jreq.request.application.RequestHistoryRepository;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.EnvironmentScope;
import com.jreq.request.domain.HistoryEnvironmentReference;
import com.jreq.request.domain.HistoryExecutionContext;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.RequestHistoryEntry;
import com.jreq.shared.database.JdbcTransactionManager;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.exception.ErrorCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class JdbcRequestHistoryRepository implements RequestHistoryRepository {
    private final SqliteConnectionFactory connectionFactory;
    private final JdbcTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    public JdbcRequestHistoryRepository(
            SqliteConnectionFactory connectionFactory,
            JdbcTransactionManager transactionManager,
            ObjectMapper objectMapper
    ) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void appendAndTrim(RequestHistoryEntry entry, int maximumEntries) {
        Objects.requireNonNull(entry, "entry");
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        String insert = """
                INSERT INTO request_history (
                    id, name, method, url, status_code, duration_ms, response_size,
                    error_code, request_json, response_json, created_at,
                    request_collection_id, environment_id, environment_name, environment_collection_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try {
            transactionManager.run(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    bindEntry(statement, entry);
                    statement.executeUpdate();
                    try (PreparedStatement trim = connection.prepareStatement("""
                            DELETE FROM request_history
                            WHERE id NOT IN (
                                SELECT id FROM request_history
                                ORDER BY created_at DESC, rowid DESC
                                LIMIT ?
                            )
                            """)) {
                        trim.setInt(1, maximumEntries);
                        trim.executeUpdate();
                    }
                }
            });
        } catch (JsonProcessingException exception) {
            throw PersistenceExceptionMapper.serialization(exception, "Unable to serialize request history");
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to save request history");
        }
    }

    @Override
    public List<RequestHistoryEntry> findRecent(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        String sql = """
                SELECT id, name, request_json, response_json, error_code, created_at,
                       request_collection_id, environment_id, environment_name, environment_collection_id
                FROM request_history
                ORDER BY created_at DESC, rowid DESC
                LIMIT ?
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<RequestHistoryEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(map(resultSet));
                }
                return List.copyOf(entries);
            }
        } catch (JsonProcessingException exception) {
            throw PersistenceExceptionMapper.serialization(exception, "Unable to deserialize request history");
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to list request history");
        }
    }

    @Override
    public void deleteById(UUID id) {
        Objects.requireNonNull(id, "id");
        try (Connection connection = connectionFactory.openConnection()) {
            JdbcCommands.executeUpdate(
                    connection,
                    "DELETE FROM request_history WHERE id = ?",
                    statement -> statement.setString(1, id.toString()));
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to delete request history");
        }
    }

    @Override
    public void deleteAll() {
        try (Connection connection = connectionFactory.openConnection()) {
            JdbcCommands.executeUpdate(connection, "DELETE FROM request_history");
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to clear request history");
        }
    }

    private void bindEntry(PreparedStatement statement, RequestHistoryEntry entry)
            throws SQLException, JsonProcessingException {
        statement.setString(1, entry.id().toString());
        statement.setString(2, entry.name());
        statement.setString(3, entry.request().method().name());
        statement.setString(4, entry.request().url());
        if (entry.result() instanceof HttpResponseSuccess success) {
            statement.setInt(5, success.statusCode());
            statement.setLong(6, success.duration().toMillis());
            statement.setLong(7, success.size());
            statement.setNull(8, Types.VARCHAR);
            statement.setString(10, objectMapper.writeValueAsString(success));
        } else {
            HttpResponseFailure failure = (HttpResponseFailure) entry.result();
            statement.setNull(5, Types.INTEGER);
            statement.setLong(6, failure.duration().toMillis());
            statement.setNull(7, Types.BIGINT);
            statement.setString(8, failure.category().name());
            statement.setString(10, objectMapper.writeValueAsString(failure));
        }
        statement.setString(9, objectMapper.writeValueAsString(entry.request()));
        statement.setString(11, entry.createdAt().toString());
        bindExecutionContext(statement, entry.executionContext());
    }

    private void bindExecutionContext(PreparedStatement statement, HistoryExecutionContext context)
            throws SQLException {
        if (context.location() instanceof RequestLocation.Collection collection) {
            statement.setString(12, collection.collectionId().toString());
        } else {
            statement.setNull(12, Types.VARCHAR);
        }
        if (context.environment() instanceof HistoryEnvironmentReference.Selected selected) {
            statement.setString(13, selected.id().toString());
            statement.setString(14, selected.name());
            if (selected.scope() instanceof EnvironmentScope.Collection collection) {
                statement.setString(15, collection.collectionId().toString());
            } else {
                statement.setNull(15, Types.VARCHAR);
            }
        } else {
            statement.setNull(13, Types.VARCHAR);
            statement.setNull(14, Types.VARCHAR);
            statement.setNull(15, Types.VARCHAR);
        }
    }

    private RequestHistoryEntry map(ResultSet resultSet) throws SQLException, JsonProcessingException {
        HttpRequestDefinition request = objectMapper.readValue(
                resultSet.getString("request_json"), HttpRequestDefinition.class);
        String responseJson = resultSet.getString("response_json");
        String errorCode = resultSet.getString("error_code");
        HttpResponseResult result;
        if (responseJson == null) {
            result = new HttpResponseFailure(
                    ErrorCategory.UNKNOWN, "The recorded response is unavailable.", Duration.ZERO);
        } else if (errorCode == null) {
            result = objectMapper.readValue(responseJson, HttpResponseSuccess.class);
        } else {
            result = objectMapper.readValue(responseJson, HttpResponseFailure.class);
        }
        String requestCollectionId = resultSet.getString("request_collection_id");
        RequestLocation location = requestCollectionId == null
                ? RequestLocation.root()
                : RequestLocation.collection(UUID.fromString(requestCollectionId));
        String environmentId = resultSet.getString("environment_id");
        HistoryEnvironmentReference environment = environmentId == null
                ? HistoryEnvironmentReference.none()
                : HistoryEnvironmentReference.selected(
                        UUID.fromString(environmentId),
                        resultSet.getString("environment_name"),
                        resultSet.getString("environment_collection_id") == null
                                ? EnvironmentScope.global()
                                : EnvironmentScope.collection(UUID.fromString(
                                        resultSet.getString("environment_collection_id"))));
        return new RequestHistoryEntry(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getString("name"),
                request,
                result,
                Instant.parse(resultSet.getString("created_at")),
                new HistoryExecutionContext(location, environment)
        );
    }

}
