package com.jreq.request.application;

import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.EnvironmentSelection;
import com.jreq.request.domain.HistoryEnvironmentReference;
import com.jreq.request.domain.HistoryExecutionContext;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.domain.RequestExecutionContext;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestHistoryEntry;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.SavedRequest;
import com.jreq.request.domain.WorkspaceName;
import com.jreq.shared.concurrent.AsyncTaskExecutor;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WorkspaceService {
    public static final HistoryLimit HISTORY_LIMIT = HistoryLimit.of(100);

    private final CollectionRepository collectionRepository;
    private final SavedRequestRepository savedRequestRepository;
    private final RequestHistoryRepository historyRepository;
    private final EnvironmentRepository environmentRepository;
    private final HttpExecutor httpExecutor;
    private final AsyncTaskExecutor databaseExecutor;
    private final RequestVariableResolver variableResolver;

    public WorkspaceService(
            CollectionRepository collectionRepository,
            SavedRequestRepository savedRequestRepository,
            RequestHistoryRepository historyRepository,
            EnvironmentRepository environmentRepository,
            HttpExecutor httpExecutor,
            AsyncTaskExecutor databaseExecutor,
            RequestVariableResolver variableResolver
    ) {
        this.collectionRepository = Objects.requireNonNull(collectionRepository, "collectionRepository");
        this.savedRequestRepository = Objects.requireNonNull(savedRequestRepository, "savedRequestRepository");
        this.historyRepository = Objects.requireNonNull(historyRepository, "historyRepository");
        this.environmentRepository = Objects.requireNonNull(environmentRepository, "environmentRepository");
        this.httpExecutor = Objects.requireNonNull(httpExecutor, "httpExecutor");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.variableResolver = Objects.requireNonNull(variableResolver, "variableResolver");
    }

    public CompletableFuture<WorkspaceSnapshot> loadWorkspace() {
        return databaseExecutor.submit(() -> new WorkspaceSnapshot(
                collectionRepository.findAll(),
                savedRequestRepository.findAll(),
                historyRepository.findRecent(HISTORY_LIMIT),
                environmentRepository.loadConfiguration(),
                environmentRepository.findActivations()
        ));
    }

    public CompletableFuture<RequestCollection> createCollection(String name) {
        String validName = WorkspaceName.require(name);
        return databaseExecutor.submit(() -> {
            Instant now = Instant.now();
            return collectionRepository.save(new RequestCollection(UUID.randomUUID(), validName, now, now));
        });
    }

    public CompletableFuture<RequestCollection> renameCollection(RequestCollection collection, String name) {
        Objects.requireNonNull(collection, "collection");
        String validName = WorkspaceName.require(name);
        return databaseExecutor.submit(() -> collectionRepository.save(collection.withName(validName)));
    }

    public CompletableFuture<Void> deleteCollection(UUID id, boolean deleteContainedRequests) {
        return databaseExecutor.submit(() -> {
            collectionRepository.deleteById(id, deleteContainedRequests);
            return null;
        });
    }

    public CompletableFuture<SavedRequest> saveRequest(
            HttpRequestDefinition request,
            RequestLocation location
    ) {
        return databaseExecutor.submit(() -> savedRequestRepository.save(request, location));
    }

    public CompletableFuture<Void> deleteRequest(UUID id) {
        return databaseExecutor.submit(() -> {
            savedRequestRepository.deleteById(id);
            return null;
        });
    }

    public CompletableFuture<ExecutionReport> executeAndRecord(
            HttpRequestDefinition request,
            RequestExecutionContext context
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        return databaseExecutor.submit(() -> prepareExecution(request, context))
                .thenCompose(prepared -> httpExecutor.execute(prepared.resolvedRequest())
                .thenCompose(result -> databaseExecutor.submit(() -> {
            RequestHistoryEntry entry = new RequestHistoryEntry(
                    UUID.randomUUID(), request.name(), request, result, Instant.now(), prepared.historyContext());
            historyRepository.appendAndTrim(entry, HISTORY_LIMIT);
            return ExecutionReport.saved(result);
        }).exceptionally(exception -> ExecutionReport.withoutHistory(result))));
    }

    public CompletableFuture<ExecutionReport> executeAndRecord(HttpRequestDefinition request) {
        return executeAndRecord(
                request,
                new RequestExecutionContext(RequestLocation.root(), EnvironmentSelection.none()));
    }

    public CompletableFuture<Void> saveEnvironmentConfiguration(EnvironmentConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return databaseExecutor.submit(() -> {
            environmentRepository.saveConfiguration(configuration);
            return null;
        });
    }

    public CompletableFuture<Void> selectEnvironment(
            RequestLocation location,
            EnvironmentSelection selection
    ) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(selection, "selection");
        return databaseExecutor.submit(() -> {
            environmentRepository.saveSelection(location, selection);
            return null;
        });
    }

    public CompletableFuture<Void> deleteHistory(UUID id) {
        return databaseExecutor.submit(() -> {
            historyRepository.deleteById(id);
            return null;
        });
    }

    public CompletableFuture<Void> clearHistory() {
        return databaseExecutor.submit(() -> {
            historyRepository.deleteAll();
            return null;
        });
    }

    private PreparedExecution prepareExecution(
            HttpRequestDefinition request,
            RequestExecutionContext context
    ) {
        EnvironmentConfiguration configuration = environmentRepository.loadConfiguration();
        Optional<RequestEnvironment> selected = selectedEnvironment(configuration, context);
        HttpRequestDefinition resolved = variableResolver.resolve(
                request, configuration.globals(), selected);
        HistoryEnvironmentReference historyEnvironment = selected
                .<HistoryEnvironmentReference>map(environment -> HistoryEnvironmentReference.selected(
                        environment.id(), environment.name(), environment.scope()))
                .orElseGet(HistoryEnvironmentReference::none);
        return new PreparedExecution(
                resolved,
                new HistoryExecutionContext(context.location(), historyEnvironment));
    }

    private Optional<RequestEnvironment> selectedEnvironment(
            EnvironmentConfiguration configuration,
            RequestExecutionContext context
    ) {
        if (context.environment() instanceof EnvironmentSelection.None) {
            return Optional.empty();
        }
        UUID id = ((EnvironmentSelection.Selected) context.environment()).environmentId();
        return Optional.of(configuration.findEnvironment(id)
                .orElseThrow(() -> new IllegalArgumentException("The selected environment no longer exists")));
    }

    private record PreparedExecution(
            HttpRequestDefinition resolvedRequest,
            HistoryExecutionContext historyContext
    ) {
    }

}
