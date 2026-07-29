package com.jreq.request.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.HttpResponseFailure;
import com.jreq.request.application.HttpResponseResult;
import com.jreq.request.application.HttpResponseSuccess;
import com.jreq.request.application.RequestHistoryRepository;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.RequestHistoryEntry;
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
    private final ObjectMapper objectMapper;

    public JdbcRequestHistoryRepository(SqliteConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
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
                    error_code, request_json, response_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);
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
                connection.commit();
            } catch (SQLException | JsonProcessingException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
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
                SELECT id, name, request_json, response_json, error_code, created_at
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
        return new RequestHistoryEntry(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getString("name"),
                request,
                result,
                Instant.parse(resultSet.getString("created_at"))
        );
    }

}
