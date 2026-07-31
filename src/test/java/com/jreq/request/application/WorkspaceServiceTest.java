package com.jreq.request.application;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.EnvironmentScope;
import com.jreq.request.domain.EnvironmentSelection;
import com.jreq.request.domain.EnvironmentVariable;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.HistoryEnvironmentReference;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.domain.RequestExecutionContext;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.infrastructure.persistence.JdbcCollectionRepository;
import com.jreq.request.infrastructure.persistence.JdbcEnvironmentRepository;
import com.jreq.request.infrastructure.persistence.JdbcRequestHistoryRepository;
import com.jreq.request.infrastructure.persistence.JdbcSavedRequestRepository;
import com.jreq.shared.database.JdbcTransactionManager;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.concurrent.ExecutorServiceTaskExecutor;
import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.json.JReqObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceServiceTest {
    @TempDir
    Path temporaryDirectory;

    private ExecutorServiceTaskExecutor databaseExecutor;
    private WorkspaceService service;
    private AtomicReference<HttpRequestDefinition> executedRequest;
    private AtomicInteger executionCount;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("service.db"));
        new DatabaseInitializer(factory).initialize();
        var mapper = JReqObjectMapper.create();
        var transactionManager = new JdbcTransactionManager(factory);
        databaseExecutor = ExecutorServiceTaskExecutor.singleThread("workspace-service-test-database");
        executedRequest = new AtomicReference<>();
        executionCount = new AtomicInteger();
        HttpExecutor httpExecutor = request -> {
            executedRequest.set(request);
            executionCount.incrementAndGet();
            return CompletableFuture.completedFuture(new HttpResponseFailure(
                    ErrorCategory.TIMEOUT, "The request timed out.", Duration.ofSeconds(1)));
        };
        service = new WorkspaceService(
                new JdbcCollectionRepository(factory, transactionManager, mapper),
                new JdbcSavedRequestRepository(factory, mapper),
                new JdbcRequestHistoryRepository(factory, transactionManager, mapper),
                new JdbcEnvironmentRepository(factory, transactionManager),
                httpExecutor,
                databaseExecutor,
                new RequestVariableResolver());
    }

    @AfterEach
    void tearDown() {
        databaseExecutor.close();
    }

    @Test
    void coordinatesCollectionAndSavedRequestCrudOffThread() throws Exception {
        var collection = service.createCollection("Local API").get(2, TimeUnit.SECONDS);
        HttpRequestDefinition request = request("Health");

        var saved = service.saveRequest(request, RequestLocation.collection(collection.id()))
                .get(2, TimeUnit.SECONDS);
        WorkspaceSnapshot snapshot = service.loadWorkspace().get(2, TimeUnit.SECONDS);

        assertThat(saved.location()).isEqualTo(RequestLocation.collection(collection.id()));
        assertThat(snapshot.collections()).containsExactly(collection);
        assertThat(snapshot.savedRequests()).containsExactly(saved);

        service.deleteRequest(request.id()).get(2, TimeUnit.SECONDS);
        assertThat(service.loadWorkspace().get(2, TimeUnit.SECONDS).savedRequests()).isEmpty();
    }

    @Test
    void recordsFailuresAndReturnsThemToTheCaller() throws Exception {
        ExecutionReport report = service.executeAndRecord(request("Timeout"))
                .get(2, TimeUnit.SECONDS);

        assertThat(report.historySaved()).isTrue();
        assertThat(report.result()).isInstanceOf(HttpResponseFailure.class);
        assertThat(service.loadWorkspace().get(2, TimeUnit.SECONDS).history())
                .singleElement()
                .extracting(entry -> entry.result())
                .isEqualTo(report.result());
    }

    @Test
    void resolvesACollectionEnvironmentFromRootAndRecordsOnlyTemplateAndContext() throws Exception {
        Instant now = Instant.now();
        var collection = service.createCollection("Pokemon").get(2, TimeUnit.SECONDS);
        RequestEnvironment environment = new RequestEnvironment(
                UUID.randomUUID(),
                "Development",
                EnvironmentScope.collection(collection.id()),
                List.of(new EnvironmentVariable(
                        UUID.randomUUID(), "host", "https://dev.example", true, true, 0)),
                now,
                now);
        service.saveEnvironmentConfiguration(new EnvironmentConfiguration(
                List.of(new EnvironmentVariable(
                        UUID.randomUUID(), "host", "https://global.example", true, false, 0)),
                List.of(environment))).get(2, TimeUnit.SECONDS);
        HttpRequestDefinition template = new HttpRequestDefinition(
                UUID.randomUUID(), "Users", HttpMethod.GET, "{{host}}/users",
                List.of(), List.of(), RequestBody.none());

        service.executeAndRecord(template, new RequestExecutionContext(
                RequestLocation.root(), EnvironmentSelection.selected(environment.id())))
                .get(2, TimeUnit.SECONDS);

        assertThat(executedRequest.get().url()).isEqualTo("https://dev.example/users");
        var history = service.loadWorkspace().get(2, TimeUnit.SECONDS).history().getFirst();
        assertThat(history.request().url()).isEqualTo("{{host}}/users");
        assertThat(history.executionContext().environment())
                .isEqualTo(HistoryEnvironmentReference.selected(
                        environment.id(), environment.name(), environment.scope()));
    }

    @Test
    void doesNotUseGlobalsAsFallbackDuringNamedEnvironmentExecution() throws Exception {
        Instant now = Instant.now();
        var collection = service.createCollection("Pokemon").get(2, TimeUnit.SECONDS);
        RequestEnvironment environment = new RequestEnvironment(
                UUID.randomUUID(),
                "Development",
                EnvironmentScope.collection(collection.id()),
                List.of(new EnvironmentVariable(
                        UUID.randomUUID(), "token", "environment-token", true, false, 0)),
                now,
                now);
        service.saveEnvironmentConfiguration(new EnvironmentConfiguration(
                List.of(new EnvironmentVariable(
                        UUID.randomUUID(), "host", "https://global.example", true, false, 0)),
                List.of(environment))).get(2, TimeUnit.SECONDS);
        HttpRequestDefinition template = new HttpRequestDefinition(
                UUID.randomUUID(), "Scoped", HttpMethod.GET, "{{host}}/users",
                List.of(), List.of(), RequestBody.none());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.executeAndRecord(
                template,
                new RequestExecutionContext(
                        RequestLocation.root(), EnvironmentSelection.selected(environment.id())))
                .get(2, TimeUnit.SECONDS)))
                .hasCauseInstanceOf(VariableResolutionException.class)
                .hasMessageContaining("missing {{host}}");
        assertThat(executionCount).hasValue(0);
    }

    @Test
    void blocksUnresolvedVariablesBeforeHttpAndHistory() throws Exception {
        HttpRequestDefinition template = new HttpRequestDefinition(
                UUID.randomUUID(), "Missing", HttpMethod.GET, "{{unknown}}/users",
                List.of(), List.of(), RequestBody.none());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.executeAndRecord(
                template,
                new RequestExecutionContext(RequestLocation.root(), EnvironmentSelection.none()))
                .get(2, TimeUnit.SECONDS)))
                .hasCauseInstanceOf(VariableResolutionException.class);
        assertThat(executionCount).hasValue(0);
        assertThat(service.loadWorkspace().get(2, TimeUnit.SECONDS).history()).isEmpty();
    }

    private HttpRequestDefinition request(String name) {
        return new HttpRequestDefinition(
                UUID.randomUUID(), name, HttpMethod.GET, "https://example.com/health",
                List.of(), List.of(), RequestBody.none());
    }
}
