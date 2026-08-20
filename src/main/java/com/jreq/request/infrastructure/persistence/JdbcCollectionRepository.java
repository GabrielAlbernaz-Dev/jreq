package com.jreq.request.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.CollectionRepository;
import com.jreq.request.application.ImportedCollection;
import com.jreq.request.domain.EnvironmentVariable;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.domain.SavedRequest;
import com.jreq.request.domain.WorkspaceName;
import com.jreq.shared.database.JdbcTransactionManager;
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
    private final JdbcTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    public JdbcCollectionRepository(
            SqliteConnectionFactory connectionFactory,
            JdbcTransactionManager transactionManager,
            ObjectMapper objectMapper
    ) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
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
    public RequestCollection saveImported(ImportedCollection imported) {
        Objects.requireNonNull(imported, "imported");
        try {
            return transactionManager.execute(connection -> {
                RequestCollection persisted = insertImportedCollection(connection, imported.collection());
                insertImportedRequests(connection, persisted.id(), imported.requests());
                insertImportedEnvironments(connection, persisted.id(), imported.environments());
                return persisted;
            });
        } catch (JsonProcessingException exception) {
            throw PersistenceExceptionMapper.serialization(
                    exception, "Unable to serialize imported requests");
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.uniqueOrDatabase(
                    exception,
                    "Unable to import the collection",
                    "A collection or request with this name already exists.");
        }
    }

    private RequestCollection insertImportedCollection(Connection connection, RequestCollection collection)
            throws SQLException {
        Set<String> usedNameKeys = findCollectionNameKeys(connection);
        String uniqueName = claimUniqueName(collection.name(), usedNameKeys);
        String sql = """
                INSERT INTO collection (id, name) VALUES (?, ?)
                RETURNING id, name, created_at, updated_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, collection.id().toString());
            statement.setString(2, uniqueName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Imported collection insert returned no row");
                }
                return mapCollection(resultSet);
            }
        }
    }

    private Set<String> findCollectionNameKeys(Connection connection) throws SQLException {
        Set<String> usedNameKeys = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT name FROM collection");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                usedNameKeys.add(WorkspaceName.comparisonKey(resultSet.getString("name")));
            }
        }
        return usedNameKeys;
    }

    private void insertImportedRequests(
            Connection connection,
            UUID collectionId,
            List<SavedRequest> requests
    ) throws SQLException, JsonProcessingException {
        String sql = """
                INSERT INTO saved_request (id, collection_id, name, method, url, definition_json)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (SavedRequest request : requests) {
                HttpRequestDefinition definition = request.definition();
                statement.setString(1, definition.id().toString());
                statement.setString(2, collectionId.toString());
                statement.setString(3, definition.name());
                statement.setString(4, definition.method().name());
                statement.setString(5, definition.url());
                statement.setString(6, objectMapper.writeValueAsString(definition));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertImportedEnvironments(
            Connection connection,
            UUID collectionId,
            List<RequestEnvironment> environments
    ) throws SQLException {
        String environmentSql = """
                INSERT INTO environment (id, collection_id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        String variableSql = """
                INSERT INTO environment_variable (
                    id, environment_id, variable_key, variable_value, enabled, secret, display_order
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement environmentStatement = connection.prepareStatement(environmentSql);
             PreparedStatement variableStatement = connection.prepareStatement(variableSql)) {
            for (RequestEnvironment environment : environments) {
                environmentStatement.setString(1, environment.id().toString());
                environmentStatement.setString(2, collectionId.toString());
                environmentStatement.setString(3, environment.name());
                environmentStatement.setString(4, environment.createdAt().toString());
                environmentStatement.setString(5, environment.updatedAt().toString());
                environmentStatement.addBatch();

                for (EnvironmentVariable variable : environment.variables()) {
                    variableStatement.setString(1, variable.id().toString());
                    variableStatement.setString(2, environment.id().toString());
                    variableStatement.setString(3, variable.key());
                    variableStatement.setString(4, variable.value());
                    variableStatement.setBoolean(5, variable.enabled());
                    variableStatement.setBoolean(6, variable.secret());
                    variableStatement.setInt(7, variable.displayOrder());
                    variableStatement.addBatch();
                }
            }
            environmentStatement.executeBatch();
            variableStatement.executeBatch();
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
        try {
            transactionManager.run(connection -> {
                if (deleteContainedRequests) {
                    removeContainedRequests(connection, id);
                } else {
                    moveContainedRequestsToRoot(connection, id);
                }
                JdbcCommands.executeUpdate(
                        connection,
                        "DELETE FROM collection WHERE id = ?",
                        statement -> statement.setString(1, id.toString()));
            });
        } catch (JsonProcessingException exception) {
            throw PersistenceExceptionMapper.serialization(
                    exception, "Unable to update requests while deleting the collection");
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
                            definition.queryParameters(), definition.headers(), definition.body(),
                            definition.authentication(), definition.cookieJarMode());
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
