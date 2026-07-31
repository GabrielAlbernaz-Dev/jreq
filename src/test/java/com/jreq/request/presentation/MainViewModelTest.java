package com.jreq.request.presentation;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.application.HttpResponseFailure;
import com.jreq.request.application.HttpResponseSuccess;
import com.jreq.request.application.WorkspaceService;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.RequestBodyType;
import com.jreq.request.domain.RequestHistoryEntry;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.SavedRequest;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class MainViewModelTest {
    @TempDir
    Path temporaryDirectory;

    private ExecutorServiceTaskExecutor databaseExecutor;
    private MainViewModel viewModel;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("view-model.db"));
        new DatabaseInitializer(factory).initialize();
        var mapper = JReqObjectMapper.create();
        var transactionManager = new JdbcTransactionManager(factory);
        databaseExecutor = ExecutorServiceTaskExecutor.singleThread("view-model-test-database");
        WorkspaceService service = new WorkspaceService(
                new JdbcCollectionRepository(factory, transactionManager, mapper),
                new JdbcSavedRequestRepository(factory, mapper),
                new JdbcRequestHistoryRepository(factory, transactionManager, mapper),
                new JdbcEnvironmentRepository(factory, transactionManager),
                request -> CompletableFuture.completedFuture(new HttpResponseFailure(
                        ErrorCategory.UNKNOWN, "Failure", Duration.ZERO)),
                databaseExecutor,
                new com.jreq.request.application.RequestVariableResolver());
        viewModel = new MainViewModel(service);
    }

    @AfterEach
    void tearDown() {
        databaseExecutor.close();
    }

    @Test
    void assemblesACompleteDefinitionAndTracksDirtyState() {
        KeyValueEntry query = new KeyValueEntry(UUID.randomUUID(), "page", "1", true);
        KeyValueEntry header = new KeyValueEntry(UUID.randomUUID(), "Accept", "application/json", true);

        assertThat(viewModel.dirtyProperty().get()).isFalse();
        viewModel.selectedMethodProperty().set(HttpMethod.POST);
        viewModel.urlProperty().set("https://example.com/users");
        viewModel.bodyTypeProperty().set(RequestBodyType.JSON);
        viewModel.requestBodyProperty().set("{\"name\":\"Ada\"}");
        viewModel.updateQueryParameters(List.of(query));
        viewModel.updateHeaders(List.of(header));

        assertThat(viewModel.dirtyProperty().get()).isTrue();
        assertThat(viewModel.definition())
                .extracting(
                        HttpRequestDefinition::method,
                        HttpRequestDefinition::url,
                        HttpRequestDefinition::queryParameters,
                        HttpRequestDefinition::headers,
                        request -> request.body().type())
                .containsExactly(
                        HttpMethod.POST,
                        "https://example.com/users",
                        List.of(query),
                        List.of(header),
                        RequestBodyType.JSON);

        viewModel.newRequest();
        assertThat(viewModel.dirtyProperty().get()).isFalse();
    }

    @Test
    void opensSavedRequestsAsCleanAndHistorySnapshotsAsUnsaved() {
        HttpRequestDefinition definition = new HttpRequestDefinition(
                UUID.randomUUID(), "Health", HttpMethod.GET, "https://example.com/health",
                List.of(), List.of(), RequestBody.none());
        Instant now = Instant.now();
        SavedRequest saved = new SavedRequest(definition, RequestLocation.root(), now, now);

        viewModel.openSavedRequest(saved);
        assertThat(viewModel.persistedProperty().get()).isTrue();
        assertThat(viewModel.dirtyProperty().get()).isFalse();

        RequestHistoryEntry history = new RequestHistoryEntry(
                UUID.randomUUID(), definition.name(), definition,
                new HttpResponseFailure(ErrorCategory.TIMEOUT, "The request timed out.", Duration.ofSeconds(1)),
                now);
        viewModel.openHistory(history);

        assertThat(viewModel.persistedProperty().get()).isFalse();
        assertThat(viewModel.dirtyProperty().get()).isTrue();
        assertThat(viewModel.responseStatusProperty().get()).isEqualTo("ERROR");
        assertThat(viewModel.responseBodyProperty().get()).isEqualTo("The request timed out.");
        assertThat(viewModel.definition().id()).isNotEqualTo(definition.id());
    }

    @Test
    void formatsJsonInBodyViewAndPreservesOriginalRawResponse() {
        HttpRequestDefinition definition = new HttpRequestDefinition(
                UUID.randomUUID(), "Users", HttpMethod.GET, "https://example.com/users",
                List.of(), List.of(), RequestBody.none());
        String compactJson = "{\"users\":[{\"name\":\"Ada\"}]}";
        byte[] responseBody = compactJson.getBytes(StandardCharsets.UTF_8);
        HttpResponseSuccess response = new HttpResponseSuccess(
                200,
                Map.of("content-type", List.of("application/json")),
                responseBody,
                Duration.ofMillis(20),
                responseBody.length);

        viewModel.openHistory(new RequestHistoryEntry(
                UUID.randomUUID(), definition.name(), definition, response, Instant.now()));

        assertThat(viewModel.responseBodyProperty().get())
                .isEqualTo("""
                        {
                          "users": [
                            {
                              "name": "Ada"
                            }
                          ]
                        }""");
        assertThat(viewModel.responseFormattingAvailableProperty().get()).isTrue();

        viewModel.responseFormattingEnabledProperty().set(false);

        assertThat(viewModel.responseBodyProperty().get()).isEqualTo(compactJson);
        viewModel.responseFormattingEnabledProperty().set(true);
        assertThat(viewModel.responseBodyProperty().get()).contains("\n");
        assertThat(viewModel.responseRawProperty().get())
                .endsWith("\n\n" + compactJson);
    }

    @Test
    void providesLiveFeedbackForVariableReferences() {
        viewModel.urlProperty().set("https://example.com/{{missing}}");

        assertThat(viewModel.variableFeedbackStateProperty().get())
                .isEqualTo(VariableFeedbackState.INVALID);
        assertThat(viewModel.variableFeedbackProperty().get()).contains("missing {{missing}}");

        viewModel.urlProperty().set("https://example.com/health");

        assertThat(viewModel.variableFeedbackStateProperty().get())
                .isEqualTo(VariableFeedbackState.NONE);
        assertThat(viewModel.variableFeedbackProperty().get()).isEmpty();
    }
}
