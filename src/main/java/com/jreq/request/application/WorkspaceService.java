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
import com.jreq.request.domain.StoredCookie;
import com.jreq.shared.concurrent.AsyncTaskExecutor;

import java.time.Instant;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WorkspaceService {
    public static final HistoryLimit HISTORY_LIMIT = HistoryLimit.of(100);

    private final CollectionRepository collectionRepository;
    private final SavedRequestRepository savedRequestRepository;
    private final RequestHistoryRepository historyRepository;
    private final EnvironmentRepository environmentRepository;
    private final CookieRepository cookieRepository;
    private final CookieJar cookieJar;
    private final HttpExecutor httpExecutor;
    private final AsyncTaskExecutor databaseExecutor;
    private final RequestVariableResolver variableResolver;
    private final RequestAuthenticationApplicator authenticationApplicator;

    public WorkspaceService(
            CollectionRepository collectionRepository,
            SavedRequestRepository savedRequestRepository,
            RequestHistoryRepository historyRepository,
            EnvironmentRepository environmentRepository,
            CookieRepository cookieRepository,
            CookieJar cookieJar,
            HttpExecutor httpExecutor,
            AsyncTaskExecutor databaseExecutor,
            RequestVariableResolver variableResolver,
            RequestAuthenticationApplicator authenticationApplicator
    ) {
        this.collectionRepository = Objects.requireNonNull(collectionRepository, "collectionRepository");
        this.savedRequestRepository = Objects.requireNonNull(savedRequestRepository, "savedRequestRepository");
        this.historyRepository = Objects.requireNonNull(historyRepository, "historyRepository");
        this.environmentRepository = Objects.requireNonNull(environmentRepository, "environmentRepository");
        this.cookieRepository = Objects.requireNonNull(cookieRepository, "cookieRepository");
        this.cookieJar = Objects.requireNonNull(cookieJar, "cookieJar");
        this.httpExecutor = Objects.requireNonNull(httpExecutor, "httpExecutor");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.variableResolver = Objects.requireNonNull(variableResolver, "variableResolver");
        this.authenticationApplicator =
                Objects.requireNonNull(authenticationApplicator, "authenticationApplicator");
    }

    public CompletableFuture<WorkspaceSnapshot> loadWorkspace() {
        return databaseExecutor.submit(() -> new WorkspaceSnapshot(
                collectionRepository.findAll(),
                savedRequestRepository.findAll(),
                historyRepository.findRecent(HISTORY_LIMIT),
                environmentRepository.loadConfiguration(),
                environmentRepository.findActivations(),
                cookieJar.snapshot()
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
                .thenCompose(result -> databaseExecutor.submit(() -> completeExecution(
                        request, prepared, result))));
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

    public CompletableFuture<Void> saveCookies(List<StoredCookie> cookies) {
        return saveCookies(CookieJarEdit.of(
                List.copyOf(Objects.requireNonNull(cookies, "cookies")),
                Set.of(),
                true));
    }

    public CompletableFuture<Void> saveCookies(CookieJarEdit edit) {
        CookieJarEdit safeEdit = Objects.requireNonNull(edit, "edit");
        return databaseExecutor.submit(() -> {
            cookieJar.applyEdit(safeEdit);
            CookieJarState state = cookieJar.state();
            cookieRepository.replaceAll(state.persistentCookies());
            cookieJar.markPersisted(state.revision());
            return null;
        });
    }

    public List<StoredCookie> cookiesFor(URI uri) {
        return cookieJar.matching(Objects.requireNonNull(uri, "uri"));
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
        HttpRequestDefinition authenticated = authenticationApplicator.apply(resolved);
        HistoryEnvironmentReference historyEnvironment = selected
                .<HistoryEnvironmentReference>map(environment -> HistoryEnvironmentReference.selected(
                        environment.id(), environment.name(), environment.scope()))
                .orElseGet(HistoryEnvironmentReference::none);
        return new PreparedExecution(
                authenticated,
                new HistoryExecutionContext(context.location(), historyEnvironment));
    }

    private ExecutionReport completeExecution(
            HttpRequestDefinition request,
            PreparedExecution prepared,
            HttpResponseResult result
    ) {
        String cookieWarning = persistCookies();
        boolean historySaved = true;
        String historyWarning = "";
        try {
            RequestHistoryEntry entry = new RequestHistoryEntry(
                    UUID.randomUUID(), request.name(), request, result, Instant.now(), prepared.historyContext());
            historyRepository.appendAndTrim(entry, HISTORY_LIMIT);
        } catch (RuntimeException historyFailure) {
            historySaved = false;
            historyWarning = "Response received, but history could not be saved.";
        }
        return ExecutionReport.completed(result, historySaved, historyWarning, cookieWarning);
    }

    private String persistCookies() {
        CookieJarState state = cookieJar.state();
        if (!state.dirty()) {
            return "";
        }
        try {
            cookieRepository.replaceAll(state.persistentCookies());
            cookieJar.markPersisted(state.revision());
            return "";
        } catch (RuntimeException cookieFailure) {
            return "Cookies were updated for this session, but could not be saved.";
        }
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
