package com.jreq.request.presentation;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.application.HttpResponseFailure;
import com.jreq.request.application.HttpResponseSuccess;
import com.jreq.request.application.BasicAuthenticationStrategy;
import com.jreq.request.application.JwtBearerAuthenticationStrategy;
import com.jreq.request.application.RequestAuthenticationApplicator;
import com.jreq.request.application.RequestVariableResolver;
import com.jreq.request.application.WorkspaceService;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.RequestBodyType;
import com.jreq.request.domain.RequestHistoryEntry;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.RequestAuthentication;
import com.jreq.request.domain.SavedRequest;
import com.jreq.request.infrastructure.persistence.JdbcCollectionRepository;
import com.jreq.request.infrastructure.persistence.JdbcEnvironmentRepository;
import com.jreq.request.infrastructure.persistence.JdbcRequestHistoryRepository;
import com.jreq.request.infrastructure.persistence.JdbcSavedRequestRepository;
import com.jreq.shared.concurrent.AsyncTaskExecutor;
import com.jreq.shared.concurrent.ExecutorServiceTaskExecutor;
import com.jreq.shared.database.JdbcTransactionManager;
import com.jreq.shared.database.SqliteConnectionFactory;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class MainViewModelTest {
    @TempDir
    Path temporaryDirectory;

    private ExecutorServiceTaskExecutor databaseExecutor;
    private WorkspaceService workspaceService;
    private MainViewModel viewModel;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("view-model.db"));
        new DatabaseInitializer(factory).initialize();
        var mapper = JReqObjectMapper.create();
        var transactionManager = new JdbcTransactionManager(factory);
        databaseExecutor = ExecutorServiceTaskExecutor.singleThread("view-model-test-database");
        workspaceService = new WorkspaceService(
                new JdbcCollectionRepository(factory, transactionManager, mapper),
                new JdbcSavedRequestRepository(factory, mapper),
                new JdbcRequestHistoryRepository(factory, transactionManager, mapper),
                new JdbcEnvironmentRepository(factory, transactionManager),
                request -> CompletableFuture.completedFuture(new HttpResponseFailure(
                        ErrorCategory.UNKNOWN, "Failure", Duration.ZERO)),
                databaseExecutor,
                new RequestVariableResolver(),
                new RequestAuthenticationApplicator(List.of(
                        new BasicAuthenticationStrategy(),
                        new JwtBearerAuthenticationStrategy())));
        viewModel = new MainViewModel(workspaceService);
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
                List.of(), List.of(), RequestBody.none(),
                new RequestAuthentication.JwtBearer("{{token}}"));
        Instant now = Instant.now();
        SavedRequest saved = new SavedRequest(definition, RequestLocation.root(), now, now);

        viewModel.openSavedRequest(saved);
        assertThat(viewModel.persistedProperty().get()).isTrue();
        assertThat(viewModel.dirtyProperty().get()).isFalse();
        assertThat(viewModel.authentication()).isEqualTo(definition.authentication());

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
        assertThat(viewModel.authentication()).isEqualTo(definition.authentication());
    }

    @Test
    void includesAuthenticationInDirtyStateAndResetsItForANewRequest() {
        RequestAuthentication.Basic basic = new RequestAuthentication.Basic("Ada", "{{password}}");

        viewModel.updateAuthentication(basic);

        assertThat(viewModel.dirtyProperty().get()).isTrue();
        assertThat(viewModel.definition().authentication()).isEqualTo(basic);

        viewModel.newRequest();

        assertThat(viewModel.authentication()).isEqualTo(RequestAuthentication.none());
        assertThat(viewModel.dirtyProperty().get()).isFalse();
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

        viewModel.responseFormattingModeProperty().set(ResponseFormattingMode.ORIGINAL);

        assertThat(viewModel.responseBodyProperty().get()).isEqualTo(compactJson);
        viewModel.responseFormattingModeProperty().set(ResponseFormattingMode.JSON);
        assertThat(viewModel.responseBodyProperty().get()).contains("\n");
        assertThat(viewModel.responseRawProperty().get())
                .endsWith("\n\n" + compactJson);
    }

    @Test
    void resetsToAutoAndReportsInvalidManualFormats() {
        viewModel.openHistory(historyEntry("Plain", "Request completed", "text/plain"));

        viewModel.responseFormattingModeProperty().set(ResponseFormattingMode.XML);

        assertThat(viewModel.responseBodyProperty().get()).isEqualTo("Request completed");
        assertThat(viewModel.responseFormattingFeedbackProperty().get())
                .contains("Could not format as XML");

        viewModel.openHistory(historyEntry(
                "XML", "<root><value>ok</value></root>", "application/xml"));

        assertThat(viewModel.responseFormattingModeProperty().get())
                .isEqualTo(ResponseFormattingMode.AUTO);
        assertThat(viewModel.detectedResponseBodyFormatProperty().get())
                .isEqualTo(ResponseBodyFormat.XML);
        assertThat(viewModel.responseBodyProperty().get()).contains("\n");
    }

    @Test
    void cachesFormattingResultsForTheCurrentResponse() {
        CountingDirectExecutor formattingExecutor = new CountingDirectExecutor();
        MainViewModel asynchronousViewModel = new MainViewModel(
                workspaceService,
                new RequestVariableResolver(),
                new ResponseBodyFormatter(JReqObjectMapper.create()),
                formattingExecutor);
        asynchronousViewModel.openHistory(historyEntry(
                "JSON", "{\"result\":true}", "application/json"));

        asynchronousViewModel.responseFormattingModeProperty().set(ResponseFormattingMode.ORIGINAL);
        asynchronousViewModel.responseFormattingModeProperty().set(ResponseFormattingMode.AUTO);
        asynchronousViewModel.responseFormattingModeProperty().set(ResponseFormattingMode.JSON);
        asynchronousViewModel.responseFormattingModeProperty().set(ResponseFormattingMode.ORIGINAL);
        asynchronousViewModel.responseFormattingModeProperty().set(ResponseFormattingMode.JSON);

        assertThat(formattingExecutor.submissionCount()).isEqualTo(2);
    }

    @Test
    void ignoresFormattingThatCompletesForAnOlderResponse() {
        QueuedTaskExecutor formattingExecutor = new QueuedTaskExecutor();
        MainViewModel asynchronousViewModel = new MainViewModel(
                workspaceService,
                new RequestVariableResolver(),
                new ResponseBodyFormatter(JReqObjectMapper.create()),
                formattingExecutor);
        asynchronousViewModel.openHistory(historyEntry(
                "First", "{\"response\":\"first\"}", "application/json"));
        asynchronousViewModel.openHistory(historyEntry(
                "Second", "{\"response\":\"second\"}", "application/json"));

        formattingExecutor.complete(0);

        assertThat(asynchronousViewModel.responseBodyProperty().get())
                .isEqualTo("{\"response\":\"second\"}");

        formattingExecutor.complete(1);

        assertThat(asynchronousViewModel.responseBodyProperty().get())
                .contains("\"response\": \"second\"")
                .doesNotContain("\"response\": \"first\"");
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

    private RequestHistoryEntry historyEntry(String name, String body, String contentType) {
        HttpRequestDefinition definition = new HttpRequestDefinition(
                UUID.randomUUID(), name, HttpMethod.GET, "https://example.com/response",
                List.of(), List.of(), RequestBody.none());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpResponseSuccess response = new HttpResponseSuccess(
                200,
                Map.of("content-type", List.of(contentType)),
                bytes,
                Duration.ofMillis(10),
                bytes.length);
        return new RequestHistoryEntry(
                UUID.randomUUID(), name, definition, response, Instant.now());
    }

    private static final class CountingDirectExecutor implements AsyncTaskExecutor {
        private int submissionCount;

        @Override
        public <T> CompletableFuture<T> submit(Callable<T> task) {
            submissionCount++;
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (Exception failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }

        private int submissionCount() {
            return submissionCount;
        }
    }

    private static final class QueuedTaskExecutor implements AsyncTaskExecutor {
        private final List<PendingTask<?>> tasks = new ArrayList<>();

        @Override
        public <T> CompletableFuture<T> submit(Callable<T> task) {
            CompletableFuture<T> future = new CompletableFuture<>();
            tasks.add(new PendingTask<>(task, future));
            return future;
        }

        private void complete(int index) {
            tasks.get(index).complete();
        }
    }

    private record PendingTask<T>(Callable<T> task, CompletableFuture<T> future) {
        private void complete() {
            try {
                future.complete(task.call());
            } catch (Exception failure) {
                future.completeExceptionally(failure);
            }
        }
    }
}
