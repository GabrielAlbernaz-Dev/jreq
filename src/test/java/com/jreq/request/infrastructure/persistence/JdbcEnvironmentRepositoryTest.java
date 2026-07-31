package com.jreq.request.infrastructure.persistence;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.application.EnvironmentActivation;
import com.jreq.request.application.EnvironmentConfiguration;
import com.jreq.request.domain.EnvironmentScope;
import com.jreq.request.domain.EnvironmentSelection;
import com.jreq.request.domain.EnvironmentVariable;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.domain.RequestLocation;
import com.jreq.shared.database.JdbcTransactionManager;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.exception.JReqException;
import com.jreq.shared.json.JReqObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcEnvironmentRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private JdbcEnvironmentRepository environments;
    private JdbcCollectionRepository collections;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("env.db"));
        new DatabaseInitializer(factory).initialize();
        JdbcTransactionManager transactions = new JdbcTransactionManager(factory);
        environments = new JdbcEnvironmentRepository(factory, transactions);
        collections = new JdbcCollectionRepository(factory, transactions, JReqObjectMapper.create());
    }

    @Test
    void savesGlobalsScopedEnvironmentsSecretsAndIndependentSelections() {
        RequestCollection collection = collections.save(collection("API"));
        RequestEnvironment global = environment("Shared", EnvironmentScope.global(), variable("host", false));
        RequestEnvironment scoped = environment(
                "Development", EnvironmentScope.collection(collection.id()), variable("token", true));
        EnvironmentConfiguration configuration = new EnvironmentConfiguration(
                List.of(variable("fallback", false)), List.of(global, scoped));

        environments.saveConfiguration(configuration);
        environments.saveSelection(RequestLocation.root(), EnvironmentSelection.selected(global.id()));
        environments.saveSelection(
                RequestLocation.collection(collection.id()), EnvironmentSelection.selected(scoped.id()));

        assertThat(environments.loadConfiguration()).isEqualTo(configuration);
        assertThat(environments.findActivations()).containsExactlyInAnyOrder(
                new EnvironmentActivation(RequestLocation.root(), EnvironmentSelection.selected(global.id())),
                new EnvironmentActivation(
                        RequestLocation.collection(collection.id()), EnvironmentSelection.selected(scoped.id())));
        assertThat(environments.loadConfiguration().environments().get(1).variables().getFirst().secret()).isTrue();

        EnvironmentVariable edited = new EnvironmentVariable(
                scoped.variables().getFirst().id(), "token", "updated-value", true, true, 0);
        environments.saveConfiguration(new EnvironmentConfiguration(
                configuration.globals(),
                List.of(global, scoped.withNameAndVariables(scoped.name(), List.of(edited)))));

        RequestEnvironment reloaded = environments.loadConfiguration().findEnvironment(scoped.id()).orElseThrow();
        assertThat(reloaded.variables().getFirst().value()).isEqualTo("updated-value");
        assertThat(environments.findActivations()).contains(new EnvironmentActivation(
                RequestLocation.collection(collection.id()), EnvironmentSelection.selected(scoped.id())));
    }

    @Test
    void selectsACollectionEnvironmentFromRoot() {
        RequestCollection owner = collections.save(collection("Pokemon"));
        RequestEnvironment scoped = environment(
                "Development", EnvironmentScope.collection(owner.id()), variable("host", false));
        environments.saveConfiguration(new EnvironmentConfiguration(List.of(), List.of(scoped)));

        environments.saveSelection(RequestLocation.root(), EnvironmentSelection.selected(scoped.id()));

        assertThat(environments.findActivations()).containsExactly(new EnvironmentActivation(
                RequestLocation.root(), EnvironmentSelection.selected(scoped.id())));
    }

    @Test
    void cascadesCollectionEnvironmentsAndSelections() {
        RequestCollection collection = collections.save(collection("API"));
        RequestEnvironment scoped = environment(
                "Development", EnvironmentScope.collection(collection.id()), variable("host", false));
        environments.saveConfiguration(new EnvironmentConfiguration(List.of(), List.of(scoped)));
        environments.saveSelection(
                RequestLocation.collection(collection.id()), EnvironmentSelection.selected(scoped.id()));

        collections.deleteById(collection.id(), false);

        assertThat(environments.loadConfiguration().environments()).isEmpty();
        assertThat(environments.findActivations()).isEmpty();
    }

    @Test
    void enforcesCaseInsensitiveEnvironmentNamesPerScope() {
        RequestEnvironment first = environment("Development", EnvironmentScope.global(), variable("a", false));
        RequestEnvironment second = environment("development", EnvironmentScope.global(), variable("b", false));

        assertThatThrownBy(() -> environments.saveConfiguration(
                new EnvironmentConfiguration(List.of(), List.of(first, second))))
                .isInstanceOf(JReqException.class)
                .hasMessageContaining("unique");
    }

    private RequestCollection collection(String name) {
        Instant now = Instant.now();
        return new RequestCollection(UUID.randomUUID(), name, now, now);
    }

    private RequestEnvironment environment(
            String name,
            EnvironmentScope scope,
            EnvironmentVariable variable
    ) {
        Instant now = Instant.now();
        return new RequestEnvironment(UUID.randomUUID(), name, scope, List.of(variable), now, now);
    }

    private EnvironmentVariable variable(String key, boolean secret) {
        return new EnvironmentVariable(UUID.randomUUID(), key, key + "-value", true, secret, 0);
    }
}
