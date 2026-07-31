package com.jreq.request.infrastructure.persistence;

import com.jreq.request.application.EnvironmentActivation;
import com.jreq.request.application.EnvironmentConfiguration;
import com.jreq.request.application.EnvironmentRepository;
import com.jreq.request.domain.EnvironmentScope;
import com.jreq.request.domain.EnvironmentSelection;
import com.jreq.request.domain.EnvironmentVariable;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.domain.RequestLocation;
import com.jreq.shared.database.JdbcTransactionManager;
import com.jreq.shared.database.SqliteConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class JdbcEnvironmentRepository implements EnvironmentRepository {
    private final SqliteConnectionFactory connectionFactory;
    private final JdbcTransactionManager transactionManager;

    public JdbcEnvironmentRepository(
            SqliteConnectionFactory connectionFactory,
            JdbcTransactionManager transactionManager
    ) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
        this.transactionManager = Objects.requireNonNull(transactionManager, "transactionManager");
    }

    @Override
    public EnvironmentConfiguration loadConfiguration() {
        try (Connection connection = connectionFactory.openConnection()) {
            List<EnvironmentVariable> globals = readGlobals(connection);
            Map<UUID, List<EnvironmentVariable>> variables = readEnvironmentVariables(connection);
            List<RequestEnvironment> environments = readEnvironments(connection, variables);
            return new EnvironmentConfiguration(globals, environments);
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to load environments");
        }
    }

    @Override
    public List<EnvironmentActivation> findActivations() {
        String sql = "SELECT context_key, collection_id, environment_id FROM environment_selection";
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<EnvironmentActivation> activations = new ArrayList<>();
            while (resultSet.next()) {
                RequestLocation location = resultSet.getString("collection_id") == null
                        ? RequestLocation.root()
                        : RequestLocation.collection(UUID.fromString(resultSet.getString("collection_id")));
                activations.add(new EnvironmentActivation(
                        location,
                        EnvironmentSelection.selected(UUID.fromString(resultSet.getString("environment_id")))));
            }
            return List.copyOf(activations);
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to load environment selections");
        }
    }

    @Override
    public void saveConfiguration(EnvironmentConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        try {
            transactionManager.run(connection -> {
                saveEnvironments(connection, configuration.environments());
                saveGlobals(connection, configuration.globals());
            });
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.uniqueOrDatabase(
                    exception,
                    "Unable to save environments",
                    "Environment names and variable keys must be unique within their scope.");
        }
    }

    @Override
    public void saveSelection(RequestLocation location, EnvironmentSelection selection) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(selection, "selection");
        try (Connection connection = connectionFactory.openConnection()) {
            if (selection instanceof EnvironmentSelection.None) {
                deleteSelection(connection, contextKey(location));
                return;
            }
            UUID environmentId = ((EnvironmentSelection.Selected) selection).environmentId();
            requireEnvironmentExists(connection, environmentId);
            String sql = """
                    INSERT INTO environment_selection (context_key, collection_id, environment_id)
                    VALUES (?, ?, ?)
                    ON CONFLICT(context_key) DO UPDATE SET
                        collection_id = excluded.collection_id,
                        environment_id = excluded.environment_id
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, contextKey(location));
                if (location instanceof RequestLocation.Collection collection) {
                    statement.setString(2, collection.collectionId().toString());
                } else {
                    statement.setNull(2, java.sql.Types.VARCHAR);
                }
                statement.setString(3, environmentId.toString());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw PersistenceExceptionMapper.database(exception, "Unable to select the environment");
        }
    }

    private List<EnvironmentVariable> readGlobals(Connection connection) throws SQLException {
        String sql = """
                SELECT id, variable_key, variable_value, enabled, secret, display_order
                FROM global_variable
                ORDER BY display_order, id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<EnvironmentVariable> variables = new ArrayList<>();
            while (resultSet.next()) {
                variables.add(mapVariable(resultSet));
            }
            return List.copyOf(variables);
        }
    }

    private Map<UUID, List<EnvironmentVariable>> readEnvironmentVariables(Connection connection)
            throws SQLException {
        String sql = """
                SELECT environment_id, id, variable_key, variable_value, enabled, secret, display_order
                FROM environment_variable
                ORDER BY environment_id, display_order, id
                """;
        Map<UUID, List<EnvironmentVariable>> variables = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                UUID environmentId = UUID.fromString(resultSet.getString("environment_id"));
                variables.computeIfAbsent(environmentId, ignored -> new ArrayList<>())
                        .add(mapVariable(resultSet));
            }
        }
        return variables;
    }

    private List<RequestEnvironment> readEnvironments(
            Connection connection,
            Map<UUID, List<EnvironmentVariable>> variables
    ) throws SQLException {
        String sql = """
                SELECT id, collection_id, name, created_at, updated_at
                FROM environment
                ORDER BY collection_id, name COLLATE NOCASE, id
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<RequestEnvironment> environments = new ArrayList<>();
            while (resultSet.next()) {
                UUID id = UUID.fromString(resultSet.getString("id"));
                String collectionId = resultSet.getString("collection_id");
                EnvironmentScope scope = collectionId == null
                        ? EnvironmentScope.global()
                        : EnvironmentScope.collection(UUID.fromString(collectionId));
                environments.add(new RequestEnvironment(
                        id,
                        resultSet.getString("name"),
                        scope,
                        variables.getOrDefault(id, List.of()),
                        Instant.parse(resultSet.getString("created_at")),
                        Instant.parse(resultSet.getString("updated_at"))));
            }
            return List.copyOf(environments);
        }
    }

    private EnvironmentVariable mapVariable(ResultSet resultSet) throws SQLException {
        return new EnvironmentVariable(
                UUID.fromString(resultSet.getString("id")),
                resultSet.getString("variable_key"),
                resultSet.getString("variable_value"),
                resultSet.getBoolean("enabled"),
                resultSet.getBoolean("secret"),
                resultSet.getInt("display_order"));
    }

    private void saveEnvironments(Connection connection, List<RequestEnvironment> environments)
            throws SQLException {
        Set<String> ids = environments.stream().map(environment -> environment.id().toString())
                .collect(Collectors.toSet());
        JdbcCommands.executeUpdate(
                connection,
                "UPDATE environment SET name = '__jreq_pending__' || id",
                ignored -> { });
        deleteMissingEnvironments(connection, ids);
        String upsert = """
                INSERT INTO environment (id, collection_id, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
                """;
        for (RequestEnvironment environment : environments) {
            try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                statement.setString(1, environment.id().toString());
                bindScope(statement, 2, environment.scope());
                statement.setString(3, environment.name());
                statement.setString(4, environment.createdAt().toString());
                statement.setString(5, environment.updatedAt().toString());
                statement.executeUpdate();
            }
            replaceVariables(connection, environment.id(), environment.variables());
        }
    }

    private void deleteMissingEnvironments(Connection connection, Set<String> retainedIds) throws SQLException {
        if (retainedIds.isEmpty()) {
            JdbcCommands.executeUpdate(connection, "DELETE FROM environment", ignored -> { });
            return;
        }
        String placeholders = retainedIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM environment WHERE id NOT IN (" + placeholders + ")")) {
            int index = 1;
            for (String id : retainedIds) {
                statement.setString(index++, id);
            }
            statement.executeUpdate();
        }
    }

    private void bindScope(PreparedStatement statement, int index, EnvironmentScope scope) throws SQLException {
        if (scope instanceof EnvironmentScope.Collection collection) {
            statement.setString(index, collection.collectionId().toString());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private void replaceVariables(
            Connection connection,
            UUID environmentId,
            List<EnvironmentVariable> variables
    ) throws SQLException {
        JdbcCommands.executeUpdate(
                connection,
                "DELETE FROM environment_variable WHERE environment_id = ?",
                statement -> statement.setString(1, environmentId.toString()));
        String insert = """
                INSERT INTO environment_variable (
                    id, environment_id, variable_key, variable_value, enabled, secret, display_order
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        for (EnvironmentVariable variable : sorted(variables)) {
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                bindVariable(statement, variable, environmentId, false);
                statement.executeUpdate();
            }
        }
    }

    private void saveGlobals(Connection connection, List<EnvironmentVariable> variables) throws SQLException {
        JdbcCommands.executeUpdate(connection, "DELETE FROM global_variable", ignored -> { });
        String insert = """
                INSERT INTO global_variable (
                    id, variable_key, variable_value, enabled, secret, display_order
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        for (EnvironmentVariable variable : sorted(variables)) {
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                bindVariable(statement, variable, null, true);
                statement.executeUpdate();
            }
        }
    }

    private List<EnvironmentVariable> sorted(List<EnvironmentVariable> variables) {
        return variables.stream().sorted(Comparator.comparingInt(EnvironmentVariable::displayOrder)).toList();
    }

    private void bindVariable(
            PreparedStatement statement,
            EnvironmentVariable variable,
            UUID environmentId,
            boolean global
    ) throws SQLException {
        int index = 1;
        statement.setString(index++, variable.id().toString());
        if (!global) {
            statement.setString(index++, environmentId.toString());
        }
        statement.setString(index++, variable.key());
        statement.setString(index++, variable.value());
        statement.setBoolean(index++, variable.enabled());
        statement.setBoolean(index++, variable.secret());
        statement.setInt(index, variable.displayOrder());
    }

    private void requireEnvironmentExists(Connection connection, UUID environmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM environment WHERE id = ?")) {
            statement.setString(1, environmentId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("The selected environment no longer exists");
                }
            }
        }
    }

    private void deleteSelection(Connection connection, String contextKey) throws SQLException {
        JdbcCommands.executeUpdate(
                connection,
                "DELETE FROM environment_selection WHERE context_key = ?",
                statement -> statement.setString(1, contextKey));
    }

    private String contextKey(RequestLocation location) {
        return location instanceof RequestLocation.Collection collection
                ? collection.collectionId().toString()
                : "ROOT";
    }
}
