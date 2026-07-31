package com.jreq.request.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.SavedRequestRepository;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.SavedRequest;
import com.jreq.shared.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcSavedRequestRepository implements SavedRequestRepository {
    private static final String UPSERT_SQL = """
            INSERT INTO saved_request (id, collection_id, name, method, url, definition_json)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                collection_id = excluded.collection_id,
                name = excluded.name,
                method = excluded.method,
                url = excluded.url,
                definition_json = excluded.definition_json,
                updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
            RETURNING collection_id, definition_json, created_at, updated_at
            """;
    private static final String SELECT_COLUMNS = """
            SELECT collection_id, definition_json, created_at, updated_at
            FROM saved_request
            """;

    private final SqliteConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public JdbcSavedRequestRepository(SqliteConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public SavedRequest save(HttpRequestDefinition request, RequestLocation location) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(location, "location");
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, request.id().toString());
            if (location instanceof RequestLocation.Collection collection) {
                statement.setString(2, collection.collectionId().toString());
            } else {
                statement.setNull(2, java.sql.Types.VARCHAR);
            }
            statement.setString(3, request.name());
            statement.setString(4, request.method().name());
            statement.setString(5, request.url());
            statement.setString(6, objectMapper.writeValueAsString(request));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Saved request upsert returned no row");
                }
                return map(resultSet);
            }
        } catch (JsonProcessingException exception) {
            throw PersistenceExceptionMapper.serialization(exception, "Unable to serialize the request");
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.uniqueOrDatabase(
                    exception,
                    "Unable to save the request",
                    "A request with this name already exists in that location.");
        }
    }

    @Override
    public Optional<SavedRequest> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        String sql = SELECT_COLUMNS + " WHERE id = ?";
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (JsonProcessingException exception) {
            throw PersistenceExceptionMapper.serialization(exception, "Unable to deserialize the request");
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to read the request");
        }
    }

    @Override
    public List<SavedRequest> findAll() {
        String sql = SELECT_COLUMNS + " ORDER BY name COLLATE NOCASE, id";
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<SavedRequest> requests = new ArrayList<>();
            while (resultSet.next()) {
                requests.add(map(resultSet));
            }
            return List.copyOf(requests);
        } catch (JsonProcessingException exception) {
            throw PersistenceExceptionMapper.serialization(exception, "Unable to deserialize saved requests");
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to list saved requests");
        }
    }

    @Override
    public void deleteById(UUID id) {
        Objects.requireNonNull(id, "id");
        try (Connection connection = connectionFactory.openConnection()) {
            JdbcCommands.executeUpdate(
                    connection,
                    "DELETE FROM saved_request WHERE id = ?",
                    statement -> statement.setString(1, id.toString()));
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to delete the request");
        }
    }

    private SavedRequest map(ResultSet resultSet) throws SQLException, JsonProcessingException {
        HttpRequestDefinition definition = objectMapper.readValue(
                resultSet.getString("definition_json"), HttpRequestDefinition.class);
        String collectionId = resultSet.getString("collection_id");
        RequestLocation location = collectionId == null
                ? RequestLocation.root()
                : RequestLocation.collection(UUID.fromString(collectionId));
        return new SavedRequest(
                definition,
                location,
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

}
