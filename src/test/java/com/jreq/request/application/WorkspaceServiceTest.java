package com.jreq.request.application;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.infrastructure.persistence.JdbcCollectionRepository;
import com.jreq.request.infrastructure.persistence.JdbcRequestHistoryRepository;
import com.jreq.request.infrastructure.persistence.JdbcSavedRequestRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceServiceTest {
    @TempDir
    Path temporaryDirectory;

    private ExecutorServiceTaskExecutor databaseExecutor;
    private WorkspaceService service;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("service.db"));
        new DatabaseInitializer(factory).initialize();
        var mapper = JReqObjectMapper.create();
        databaseExecutor = ExecutorServiceTaskExecutor.singleThread("workspace-service-test-database");
        HttpExecutor httpExecutor = request -> CompletableFuture.completedFuture(
                new HttpResponseFailure(
                        ErrorCategory.TIMEOUT, "The request timed out.", Duration.ofSeconds(1)));
        service = new WorkspaceService(
                new JdbcCollectionRepository(factory, mapper),
                new JdbcSavedRequestRepository(factory, mapper),
                new JdbcRequestHistoryRepository(factory, mapper),
                httpExecutor,
                databaseExecutor);
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

    private HttpRequestDefinition request(String name) {
        return new HttpRequestDefinition(
                UUID.randomUUID(), name, HttpMethod.GET, "https://example.com/health",
                List.of(), List.of(), RequestBody.none());
    }
}
