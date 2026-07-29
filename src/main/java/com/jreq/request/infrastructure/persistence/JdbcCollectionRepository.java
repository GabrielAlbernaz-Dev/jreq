package com.jreq.request.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.CollectionRepository;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.WorkspaceName;
import com.jreq.shared.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class JdbcCollectionRepository implements CollectionRepository {
    private static final String SELECT_COLUMNS = "SELECT id, name, created_at, updated_at FROM collection";
    private final SqliteConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public JdbcCollectionRepository(SqliteConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public RequestCollection save(RequestCollection collection) {
        Objects.requireNonNull(collection, "collection");
        String sql = """
                INSERT INTO collection (id, name) VALUES (?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
                RETURNING id, name, created_at, updated_at
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, collection.id().toString());
            statement.setString(2, collection.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Collection upsert returned no row");
                }
                return mapCollection(resultSet);
            }
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.uniqueOrDatabase(
                    exception,
                    "Unable to save the collection",
                    "A collection with this name already exists.");
        }
    }

    @Override
    public Optional<RequestCollection> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_COLUMNS + " WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapCollection(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to read the collection");
        }
    }

    @Override
    public List<RequestCollection> findAll() {
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT_COLUMNS + " ORDER BY name COLLATE NOCASE, id");
             ResultSet resultSet = statement.executeQuery()) {
            List<RequestCollection> collections = new ArrayList<>();
            while (resultSet.next()) {
                collections.add(mapCollection(resultSet));
            }
            return List.copyOf(collections);
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to list collections");
        }
    }

    @Override
    public void deleteById(UUID id, boolean deleteContainedRequests) {
        Objects.requireNonNull(id, "id");
        try (Connection connection = connectionFactory.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (deleteContainedRequests) {
                    removeContainedRequests(connection, id);
                } else {
                    moveContainedRequestsToRoot(connection, id);
                }
                JdbcCommands.executeUpdate(
                        connection,
                        "DELETE FROM collection WHERE id = ?",
                        statement -> statement.setString(1, id.toString()));
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw PersistenceExceptionMapper.database(exception, "Unable to delete the collection");
            } catch (JsonProcessingException exception) {
                rollback(connection, exception);
                throw PersistenceExceptionMapper.serialization(
                        exception, "Unable to update requests while deleting the collection");
            } catch (RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to delete the collection");
        }
    }

    private void removeContainedRequests(Connection connection, UUID collectionId) throws SQLException {
        JdbcCommands.executeUpdate(
                connection,
                "DELETE FROM saved_request WHERE collection_id = ?",
                statement -> statement.setString(1, collectionId.toString()));
    }

    private void moveContainedRequestsToRoot(Connection connection, UUID collectionId)
            throws SQLException, JsonProcessingException {
        Set<String> usedNameKeys = findRootRequestNameKeys(connection);
        List<RequestMove> moves = findMoves(connection, collectionId, usedNameKeys);
        updateRequests(connection, moves);
    }

    private Set<String> findRootRequestNameKeys(Connection connection) throws SQLException {
        Set<String> usedNameKeys = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name FROM saved_request WHERE collection_id IS NULL");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                usedNameKeys.add(WorkspaceName.comparisonKey(resultSet.getString("name")));
            }
        }
        return usedNameKeys;
    }

    private List<RequestMove> findMoves(
            Connection connection,
            UUID collectionId,
            Set<String> usedNameKeys
    ) throws SQLException, JsonProcessingException {
        String sql = """
                SELECT id, name, definition_json
                FROM saved_request
                WHERE collection_id = ?
                ORDER BY name COLLATE NOCASE, id
                """;
        List<RequestMove> moves = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, collectionId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String uniqueName = claimUniqueName(resultSet.getString("name"), usedNameKeys);
                    HttpRequestDefinition definition = objectMapper.readValue(
                            resultSet.getString("definition_json"), HttpRequestDefinition.class);
                    HttpRequestDefinition renamed = new HttpRequestDefinition(
                            definition.id(), uniqueName, definition.method(), definition.url(),
                            definition.queryParameters(), definition.headers(), definition.body());
                    moves.add(new RequestMove(
                            resultSet.getString("id"), uniqueName, objectMapper.writeValueAsString(renamed)));
                }
            }
        }
        return moves;
    }

    private String claimUniqueName(String requestedName, Set<String> usedNameKeys) {
        String candidate = WorkspaceName.require(requestedName);
        int suffix = 2;
        while (!usedNameKeys.add(WorkspaceName.comparisonKey(candidate))) {
            candidate = requestedName + " (" + suffix++ + ")";
        }
        return candidate;
    }

    private void updateRequests(Connection connection, List<RequestMove> moves) throws SQLException {
        String sql = """
                UPDATE saved_request
                SET collection_id = NULL,
                    name = ?,
                    definition_json = ?,
                    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (RequestMove move : moves) {
                statement.setString(1, move.name());
                statement.setString(2, move.definitionJson());
                statement.setString(3, move.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void rollback(Connection connection, Throwable originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    private RequestCollection mapCollection(ResultSet resultSet) throws SQLException {
        return new RequestCollection(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getString("name"),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private record RequestMove(String id, String name, String definitionJson) {
    }
}
