package com.jreq.request.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.SavedRequestRepository;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.exception.JReqException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcSavedRequestRepository implements SavedRequestRepository {
    private static final String UPSERT_SQL = """
            INSERT INTO saved_request (id, name, method, url, definition_json)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                method = excluded.method,
                url = excluded.url,
                definition_json = excluded.definition_json,
                updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
            """;
    private static final String FIND_SQL = "SELECT definition_json FROM saved_request WHERE id = ?";

    private final SqliteConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public JdbcSavedRequestRepository(SqliteConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void save(HttpRequestDefinition request) {
        Objects.requireNonNull(request, "request");
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, request.id().toString());
            statement.setString(2, request.name());
            statement.setString(3, request.method().name());
            statement.setString(4, request.url());
            statement.setString(5, objectMapper.writeValueAsString(request));
            statement.executeUpdate();
        } catch (JsonProcessingException exception) {
            throw new JReqException(ErrorCategory.SERIALIZATION_ERROR,
                    "Unable to serialize the request", exception);
        } catch (SQLException exception) {
            throw new JReqException(ErrorCategory.DATABASE_ERROR,
                    "Unable to save the request", exception);
        }
    }

    @Override
    public Optional<HttpRequestDefinition> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_SQL)) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(objectMapper.readValue(
                        resultSet.getString("definition_json"), HttpRequestDefinition.class));
            }
        } catch (JsonProcessingException exception) {
            throw new JReqException(ErrorCategory.SERIALIZATION_ERROR,
                    "Unable to deserialize the request", exception);
        } catch (SQLException exception) {
            throw new JReqException(ErrorCategory.DATABASE_ERROR,
                    "Unable to read the request", exception);
        }
    }
}
