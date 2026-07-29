package com.jreq.request.application;

import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestHistoryEntry;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.SavedRequest;
import com.jreq.request.domain.WorkspaceName;
import com.jreq.shared.concurrent.AsyncTaskExecutor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WorkspaceService {
    public static final int HISTORY_LIMIT = 100;

    private final CollectionRepository collectionRepository;
    private final SavedRequestRepository savedRequestRepository;
    private final RequestHistoryRepository historyRepository;
    private final HttpExecutor httpExecutor;
    private final AsyncTaskExecutor databaseExecutor;

    public WorkspaceService(
            CollectionRepository collectionRepository,
            SavedRequestRepository savedRequestRepository,
            RequestHistoryRepository historyRepository,
            HttpExecutor httpExecutor,
            AsyncTaskExecutor databaseExecutor
    ) {
        this.collectionRepository = Objects.requireNonNull(collectionRepository, "collectionRepository");
        this.savedRequestRepository = Objects.requireNonNull(savedRequestRepository, "savedRequestRepository");
        this.historyRepository = Objects.requireNonNull(historyRepository, "historyRepository");
        this.httpExecutor = Objects.requireNonNull(httpExecutor, "httpExecutor");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
    }

    public CompletableFuture<WorkspaceSnapshot> loadWorkspace() {
        return databaseExecutor.submit(() -> new WorkspaceSnapshot(
                collectionRepository.findAll(),
                savedRequestRepository.findAll(),
                historyRepository.findRecent(HISTORY_LIMIT)
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

    public CompletableFuture<ExecutionReport> executeAndRecord(HttpRequestDefinition request) {
        Objects.requireNonNull(request, "request");
        return httpExecutor.execute(request).thenCompose(result -> databaseExecutor.submit(() -> {
            RequestHistoryEntry entry = new RequestHistoryEntry(
                    UUID.randomUUID(), request.name(), request, result, Instant.now());
            historyRepository.appendAndTrim(entry, HISTORY_LIMIT);
            return ExecutionReport.saved(result);
        }).exceptionally(exception -> ExecutionReport.withoutHistory(result)));
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

}
