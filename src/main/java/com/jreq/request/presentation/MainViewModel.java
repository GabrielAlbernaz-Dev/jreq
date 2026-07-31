package com.jreq.request.presentation;

import com.jreq.request.application.ExecutionReport;
import com.jreq.request.application.EnvironmentActivation;
import com.jreq.request.application.EnvironmentConfiguration;
import com.jreq.request.application.HttpResponseFailure;
import com.jreq.request.application.HttpResponseResult;
import com.jreq.request.application.HttpResponseSuccess;
import com.jreq.request.application.RequestVariableResolver;
import com.jreq.request.application.VariableResolutionStatus;
import com.jreq.request.application.WorkspaceService;
import com.jreq.request.application.WorkspaceSnapshot;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.EnvironmentSelection;
import com.jreq.request.domain.HistoryEnvironmentReference;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.RequestBodyType;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestHistoryEntry;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.domain.RequestExecutionContext;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.SavedRequest;
import com.jreq.request.domain.WorkspaceName;
import com.jreq.shared.ui.ResponsiveLayoutMode;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

public final class MainViewModel {
    private final WorkspaceService workspaceService;
    private final RequestVariableResolver variableResolver;

    private final ObjectProperty<HttpMethod> selectedMethod =
            new SimpleObjectProperty<>(HttpMethod.GET);
    private final StringProperty url = new SimpleStringProperty("");
    private final StringProperty requestName = new SimpleStringProperty("Untitled Request");
    private final ObjectProperty<RequestBodyType> bodyType =
            new SimpleObjectProperty<>(RequestBodyType.NONE);
    private final StringProperty requestBody = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final BooleanProperty persisted = new SimpleBooleanProperty(false);
    private final BooleanProperty sidebarExpanded = new SimpleBooleanProperty(true);
    private final StringProperty selectedRequestTab = new SimpleStringProperty("Params");
    private final StringProperty selectedResponseTab = new SimpleStringProperty("Body");
    private final StringProperty responseStatus = new SimpleStringProperty("—");
    private final StringProperty responseDuration = new SimpleStringProperty("—");
    private final StringProperty responseSize = new SimpleStringProperty("—");
    private final StringProperty responseBody = new SimpleStringProperty("");
    private final StringProperty responseHeaders = new SimpleStringProperty("");
    private final StringProperty responseRaw = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("Ready");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty variableFeedback = new SimpleStringProperty("");
    private final ObjectProperty<VariableFeedbackState> variableFeedbackState =
            new SimpleObjectProperty<>(VariableFeedbackState.NONE);
    private final ObjectProperty<VariableResolutionStatus> variableResolutionStatus =
            new SimpleObjectProperty<>(new VariableResolutionStatus(0, List.of(), java.util.Set.of()));
    private final ObjectProperty<ResponsiveLayoutMode> responsiveMode =
            new SimpleObjectProperty<>(ResponsiveLayoutMode.NORMAL);
    private final ObjectProperty<RequestLocation> requestLocation =
            new SimpleObjectProperty<>(RequestLocation.root());
    private final ObjectProperty<EnvironmentSelection> selectedEnvironment =
            new SimpleObjectProperty<>(EnvironmentSelection.none());

    private final ObservableList<RequestCollection> collections = FXCollections.observableArrayList();
    private final ObservableList<SavedRequest> savedRequests = FXCollections.observableArrayList();
    private final ObservableList<RequestHistoryEntry> history = FXCollections.observableArrayList();
    private final ObservableList<RequestEnvironment> environments = FXCollections.observableArrayList();

    private UUID requestId = UUID.randomUUID();
    private List<KeyValueEntry> queryParameters = List.of(KeyValueEntry.empty());
    private List<KeyValueEntry> headers = List.of(KeyValueEntry.empty());
    private Optional<EditorSnapshot> baseline = Optional.empty();
    private boolean changingEditor;
    private EnvironmentConfiguration environmentConfiguration = EnvironmentConfiguration.empty();
    private List<EnvironmentActivation> environmentActivations = List.of();

    public MainViewModel(WorkspaceService workspaceService) {
        this(workspaceService, new RequestVariableResolver());
    }

    public MainViewModel(WorkspaceService workspaceService, RequestVariableResolver variableResolver) {
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService");
        this.variableResolver = Objects.requireNonNull(variableResolver, "variableResolver");
        selectedMethod.addListener((observable, oldValue, newValue) -> recomputeDirty());
        url.addListener((observable, oldValue, newValue) -> editorValueChanged());
        bodyType.addListener((observable, oldValue, newValue) -> editorValueChanged());
        requestBody.addListener((observable, oldValue, newValue) -> editorValueChanged());
        requestLocation.addListener((observable, oldValue, newValue) -> {
            recomputeDirty();
            syncSelectedEnvironment();
            refreshVariableFeedback();
        });
        selectedEnvironment.addListener((observable, oldValue, newValue) -> refreshVariableFeedback());
        baseline = Optional.of(snapshot());
        refreshVariableFeedback();
    }

    public CompletableFuture<Void> initializeWorkspace() {
        statusMessage.set("Loading local workspace…");
        return applyFuture(workspaceService.loadWorkspace(), snapshot -> {
            applyWorkspace(snapshot);
            statusMessage.set("Ready");
        });
    }

    public void sendRequest() {
        if (loading.get()) {
            return;
        }
        if (url.get().isBlank()) {
            statusMessage.set("Enter a request URL");
            return;
        }
        HttpRequestDefinition request = definition();
        loading.set(true);
        statusMessage.set("Sending request…");
        workspaceService.executeAndRecord(
                        request,
                        new RequestExecutionContext(requestLocation.get(), selectedEnvironment.get()))
                .thenCompose(report -> workspaceService.loadWorkspace()
                        .thenApply(snapshot -> new SendCompletion(report, snapshot)))
                .whenComplete((completion, failure) -> onFx(() -> {
                    loading.set(false);
                    if (failure != null) {
                        showFailure(failure, "Unable to execute the request.");
                        return;
                    }
                    applyWorkspace(completion.snapshot());
                    renderResult(completion.report().result());
                    statusMessage.set(completion.report().historySaved()
                            ? "Request completed and added to history"
                            : completion.report().warning());
                }));
    }

    public void newRequest() {
        changingEditor = true;
        requestId = UUID.randomUUID();
        requestName.set("Untitled Request");
        selectedMethod.set(HttpMethod.GET);
        url.set("");
        queryParameters = List.of(KeyValueEntry.empty());
        headers = List.of(KeyValueEntry.empty());
        bodyType.set(RequestBodyType.NONE);
        requestBody.set("");
        requestLocation.set(RequestLocation.root());
        persisted.set(false);
        clearResponse();
        changingEditor = false;
        baseline = Optional.of(snapshot());
        dirty.set(false);
        statusMessage.set("New unsaved request");
        refreshVariableFeedback();
    }

    public void openSavedRequest(SavedRequest savedRequest) {
        Objects.requireNonNull(savedRequest, "savedRequest");
        loadDefinition(savedRequest.definition(), savedRequest.location(), true);
        baseline = Optional.of(snapshot());
        dirty.set(false);
        clearResponse();
        statusMessage.set("Opened " + savedRequest.definition().name());
    }

    public void openHistory(RequestHistoryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        RequestLocation restoredLocation = existingLocation(entry.executionContext().location());
        HttpRequestDefinition detached = new HttpRequestDefinition(
                UUID.randomUUID(), entry.request().name(), entry.request().method(), entry.request().url(),
                entry.request().queryParameters(), entry.request().headers(), entry.request().body());
        loadDefinition(detached, restoredLocation, false);
        baseline = Optional.empty();
        dirty.set(true);
        renderResult(entry.result());
        boolean contextRestored = restoreHistoryEnvironment(entry, restoredLocation);
        statusMessage.set(contextRestored
                ? "Opened history snapshot with its environment — save to keep it"
                : "Opened history snapshot — the original environment is unavailable");
    }

    public CompletableFuture<SavedRequest> saveCurrent() {
        if (!persisted.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Choose a name and location before saving."));
        }
        return saveDefinition(definition(), requestLocation.get());
    }

    public CompletableFuture<SavedRequest> saveNew(String name, RequestLocation location) {
        HttpRequestDefinition current = definition();
        HttpRequestDefinition named = copyDefinition(current, current.id(), name);
        return saveDefinition(named, location);
    }

    public CompletableFuture<SavedRequest> saveAs(String name, RequestLocation location) {
        HttpRequestDefinition copy = copyDefinition(definition(), UUID.randomUUID(), name);
        return saveDefinition(copy, location);
    }

    public CompletableFuture<Void> renameRequest(SavedRequest savedRequest, String name) {
        HttpRequestDefinition renamed = copyDefinition(
                savedRequest.definition(), savedRequest.definition().id(), name);
        return applyFuture(workspaceService.saveRequest(renamed, savedRequest.location()), updated -> {
            replaceSaved(updated);
            if (requestId.equals(updated.definition().id())) {
                loadDefinition(updated.definition(), updated.location(), true);
                baseline = Optional.of(snapshot());
                dirty.set(false);
            }
        });
    }

    public CompletableFuture<Void> moveRequest(SavedRequest savedRequest, RequestLocation location) {
        return applyFuture(workspaceService.saveRequest(savedRequest.definition(), location), updated -> {
            replaceSaved(updated);
            if (requestId.equals(updated.definition().id())) {
                requestLocation.set(updated.location());
                baseline = Optional.of(snapshot());
                dirty.set(false);
            }
        });
    }

    public CompletableFuture<Void> duplicateRequest(
            SavedRequest savedRequest,
            String name,
            RequestLocation location
    ) {
        HttpRequestDefinition copy = copyDefinition(savedRequest.definition(), UUID.randomUUID(), name);
        return applyFuture(workspaceService.saveRequest(copy, location), this::replaceSaved);
    }

    public CompletableFuture<Void> deleteRequest(SavedRequest savedRequest) {
        return applyFuture(workspaceService.deleteRequest(savedRequest.definition().id()), ignored -> {
            savedRequests.removeIf(item -> item.definition().id().equals(savedRequest.definition().id()));
            if (requestId.equals(savedRequest.definition().id())) {
                newRequest();
            }
        });
    }

    public CompletableFuture<Void> createCollection(String name) {
        return applyFuture(workspaceService.createCollection(name), collection -> {
            collections.add(collection);
            sortCollections();
            statusMessage.set("Collection created");
        });
    }

    public CompletableFuture<Void> renameCollection(RequestCollection collection, String name) {
        return applyFuture(workspaceService.renameCollection(collection, name), updated -> {
            collections.removeIf(item -> item.id().equals(updated.id()));
            collections.add(updated);
            sortCollections();
            statusMessage.set("Collection renamed");
        });
    }

    public CompletableFuture<Void> deleteCollection(RequestCollection collection, boolean deleteContainedRequests) {
        CompletableFuture<WorkspaceSnapshot> operation = workspaceService
                .deleteCollection(collection.id(), deleteContainedRequests)
                .thenCompose(ignored -> workspaceService.loadWorkspace());
        return applyFuture(operation, updatedWorkspace -> {
            applyWorkspace(updatedWorkspace);
            if (isInCollection(requestLocation.get(), collection.id())) {
                if (deleteContainedRequests && persisted.get()) {
                    newRequest();
                } else if (persisted.get()) {
                    savedRequests.stream()
                            .filter(item -> item.definition().id().equals(requestId))
                            .findFirst()
                            .ifPresent(this::openSavedRequest);
                } else {
                    requestLocation.set(RequestLocation.root());
                    recomputeDirty();
                }
            }
            statusMessage.set(deleteContainedRequests
                    ? "Collection and contained requests deleted"
                    : "Collection deleted; requests moved to the root");
        });
    }

    public CompletableFuture<Void> deleteHistory(RequestHistoryEntry entry) {
        return applyFuture(workspaceService.deleteHistory(entry.id()), ignored -> {
            history.removeIf(item -> item.id().equals(entry.id()));
            statusMessage.set("History entry deleted");
        });
    }

    public CompletableFuture<Void> clearHistory() {
        return applyFuture(workspaceService.clearHistory(), ignored -> {
            history.clear();
            statusMessage.set("History cleared");
        });
    }

    public CompletableFuture<Void> selectEnvironment(EnvironmentSelection selection) {
        Objects.requireNonNull(selection, "selection");
        RequestLocation location = requestLocation.get();
        return applyFuture(workspaceService.selectEnvironment(location, selection), ignored -> {
            List<EnvironmentActivation> updated = new java.util.ArrayList<>(environmentActivations.stream()
                    .filter(activation -> !activation.location().equals(location))
                    .toList());
            if (selection instanceof EnvironmentSelection.Selected) {
                updated.add(new EnvironmentActivation(location, selection));
            }
            environmentActivations = List.copyOf(updated);
            selectedEnvironment.set(selection);
            statusMessage.set(selection instanceof EnvironmentSelection.Selected
                    ? "Environment selected"
                    : "Using global variables only");
        });
    }

    public CompletableFuture<Void> saveEnvironmentConfiguration(EnvironmentConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        CompletableFuture<WorkspaceSnapshot> operation = workspaceService
                .saveEnvironmentConfiguration(configuration)
                .thenCompose(ignored -> workspaceService.loadWorkspace());
        return applyFuture(operation, snapshot -> {
            applyWorkspace(snapshot);
            statusMessage.set("Environments saved");
        });
    }

    public void updateQueryParameters(List<KeyValueEntry> entries) {
        queryParameters = List.copyOf(entries);
        recomputeDirty();
        refreshVariableFeedback();
    }

    public void updateHeaders(List<KeyValueEntry> entries) {
        headers = List.copyOf(entries);
        recomputeDirty();
        refreshVariableFeedback();
    }

    public List<KeyValueEntry> queryParameters() {
        return queryParameters;
    }

    public List<KeyValueEntry> headers() {
        return headers;
    }

    public HttpRequestDefinition definition() {
        return new HttpRequestDefinition(
                requestId,
                requestName.get(),
                selectedMethod.get(),
                url.get(),
                queryParameters,
                headers,
                createBody()
        );
    }

    private CompletableFuture<SavedRequest> saveDefinition(
            HttpRequestDefinition request,
            RequestLocation location
    ) {
        CompletableFuture<SavedRequest> result = new CompletableFuture<>();
        statusMessage.set("Saving request…");
        workspaceService.saveRequest(request, location).whenComplete((saved, failure) -> onFx(() -> {
            if (failure != null) {
                showFailure(failure, "Unable to save the request.");
                result.completeExceptionally(unwrap(failure));
                return;
            }
            replaceSaved(saved);
            loadDefinition(saved.definition(), saved.location(), true);
            baseline = Optional.of(snapshot());
            dirty.set(false);
            statusMessage.set("Request saved");
            result.complete(saved);
        }));
        return result;
    }

    private <T> CompletableFuture<Void> applyFuture(CompletableFuture<T> future, Consumer<T> success) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        future.whenComplete((value, failure) -> onFx(() -> {
            if (failure != null) {
                showFailure(failure, "The local operation could not be completed.");
                result.completeExceptionally(unwrap(failure));
                return;
            }
            success.accept(value);
            result.complete(null);
        }));
        return result;
    }

    private void loadDefinition(HttpRequestDefinition definition, RequestLocation location, boolean isPersisted) {
        changingEditor = true;
        requestId = definition.id();
        requestName.set(definition.name());
        selectedMethod.set(definition.method());
        url.set(definition.url());
        queryParameters = definition.queryParameters();
        headers = definition.headers();
        bodyType.set(definition.body().type());
        requestBody.set(definition.body().content());
        requestLocation.set(location);
        persisted.set(isPersisted);
        changingEditor = false;
        refreshVariableFeedback();
    }

    private RequestBody createBody() {
        return switch (bodyType.get()) {
            case NONE -> RequestBody.none();
            case JSON -> RequestBody.json(requestBody.get());
            case RAW_TEXT -> RequestBody.rawText(requestBody.get());
        };
    }

    private HttpRequestDefinition copyDefinition(HttpRequestDefinition source, UUID id, String name) {
        return new HttpRequestDefinition(
                id,
                name,
                source.method(), source.url(), source.queryParameters(), source.headers(), source.body());
    }

    private void applyWorkspace(WorkspaceSnapshot snapshot) {
        collections.setAll(snapshot.collections());
        savedRequests.setAll(snapshot.savedRequests());
        history.setAll(snapshot.history());
        environmentConfiguration = snapshot.environmentConfiguration();
        environmentActivations = snapshot.environmentActivations();
        environments.setAll(environmentConfiguration.environments());
        syncSelectedEnvironment();
        refreshVariableFeedback();
    }

    private void editorValueChanged() {
        recomputeDirty();
        refreshVariableFeedback();
    }

    private void refreshVariableFeedback() {
        if (changingEditor) {
            return;
        }
        VariableResolutionStatus status = variableResolver.inspect(
                definition(), environmentConfiguration.globals(), selectedEnvironment());
        variableResolutionStatus.set(status);
        if (!status.hasReferences()) {
            variableFeedback.set("");
            variableFeedbackState.set(VariableFeedbackState.NONE);
            return;
        }
        if (status.isResolved()) {
            String noun = status.referenceCount() == 1 ? "variable" : "variables";
            variableFeedback.set("✓ " + status.referenceCount() + " " + noun + " resolved");
            variableFeedbackState.set(VariableFeedbackState.RESOLVED);
            return;
        }
        variableFeedback.set("⚠ " + String.join(" · ", status.issues()));
        variableFeedbackState.set(VariableFeedbackState.INVALID);
    }

    private Optional<RequestEnvironment> selectedEnvironment() {
        if (selectedEnvironment.get() instanceof EnvironmentSelection.Selected selected) {
            return environmentConfiguration.findEnvironment(selected.environmentId());
        }
        return Optional.empty();
    }

    private void syncSelectedEnvironment() {
        EnvironmentSelection selection = environmentActivations.stream()
                .filter(activation -> activation.location().equals(requestLocation.get()))
                .map(EnvironmentActivation::selection)
                .findFirst()
                .filter(this::environmentExists)
                .orElseGet(EnvironmentSelection::none);
        selectedEnvironment.set(selection);
    }

    private boolean environmentExists(EnvironmentSelection selection) {
        if (selection instanceof EnvironmentSelection.None) {
            return true;
        }
        UUID id = ((EnvironmentSelection.Selected) selection).environmentId();
        return environmentConfiguration.findEnvironment(id).isPresent();
    }

    private RequestLocation existingLocation(RequestLocation location) {
        if (location instanceof RequestLocation.Collection collection
                && collections.stream().noneMatch(item -> item.id().equals(collection.collectionId()))) {
            return RequestLocation.root();
        }
        return location;
    }

    private boolean restoreHistoryEnvironment(RequestHistoryEntry entry, RequestLocation location) {
        if (entry.executionContext().environment() instanceof HistoryEnvironmentReference.None) {
            selectedEnvironment.set(EnvironmentSelection.none());
            return true;
        }
        HistoryEnvironmentReference.Selected reference =
                (HistoryEnvironmentReference.Selected) entry.executionContext().environment();
        EnvironmentSelection selection = EnvironmentSelection.selected(reference.id());
        if (!environmentExists(selection)) {
            selectedEnvironment.set(EnvironmentSelection.none());
            return false;
        }
        selectEnvironment(selection);
        return location.equals(entry.executionContext().location());
    }

    private void replaceSaved(SavedRequest savedRequest) {
        savedRequests.removeIf(item -> item.definition().id().equals(savedRequest.definition().id()));
        savedRequests.add(savedRequest);
        savedRequests.sort(Comparator.comparing(
                item -> WorkspaceName.comparisonKey(item.definition().name())));
    }

    private void sortCollections() {
        collections.sort(Comparator.comparing(item -> WorkspaceName.comparisonKey(item.name())));
    }

    private boolean isInCollection(RequestLocation location, UUID collectionId) {
        return location instanceof RequestLocation.Collection selected
                && selected.collectionId().equals(collectionId);
    }

    private EditorSnapshot snapshot() {
        return new EditorSnapshot(definition(), requestLocation.get());
    }

    private void recomputeDirty() {
        if (changingEditor) {
            return;
        }
        dirty.set(baseline.map(value -> !value.equals(snapshot())).orElse(true));
    }

    private void renderResult(HttpResponseResult result) {
        if (result instanceof HttpResponseSuccess success) {
            responseStatus.set(Integer.toString(success.statusCode()));
            responseDuration.set(formatDuration(success.duration()));
            responseSize.set(formatSize(success.size()));
            responseBody.set(success.bodyAsUtf8());
            String formattedHeaders = success.headers().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                    .flatMap(entry -> entry.getValue().stream()
                            .map(value -> entry.getKey() + ": " + value))
                    .collect(java.util.stream.Collectors.joining("\n"));
            responseHeaders.set(formattedHeaders);
            responseRaw.set("HTTP " + success.statusCode()
                    + (formattedHeaders.isEmpty() ? "" : "\n" + formattedHeaders)
                    + "\n\n" + success.bodyAsUtf8());
        } else {
            HttpResponseFailure failure = (HttpResponseFailure) result;
            responseStatus.set("ERROR");
            responseDuration.set(formatDuration(failure.duration()));
            responseSize.set("—");
            responseBody.set(failure.userMessage());
            responseHeaders.set("");
            responseRaw.set(failure.category().name() + "\n\n" + failure.userMessage());
        }
    }

    private void clearResponse() {
        responseBody.set("");
        responseHeaders.set("");
        responseRaw.set("");
        responseStatus.set("—");
        responseDuration.set("—");
        responseSize.set("—");
    }

    private String formatDuration(Duration duration) {
        return duration.toMillis() + " ms";
    }

    private String formatSize(long bytes) {
        if (bytes < 1_024) {
            return bytes + " B";
        }
        return String.format(Locale.ROOT, "%.1f KB", bytes / 1_024.0);
    }

    private void showFailure(Throwable failure, String fallback) {
        Throwable cause = unwrap(failure);
        String message = cause.getMessage();
        errorMessage.set(message == null || message.isBlank() ? fallback : message);
        statusMessage.set("Operation failed");
    }

    private Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public void toggleSidebar() {
        sidebarExpanded.set(!sidebarExpanded.get());
    }

    public ObjectProperty<HttpMethod> selectedMethodProperty() { return selectedMethod; }
    public StringProperty urlProperty() { return url; }
    public StringProperty requestNameProperty() { return requestName; }
    public ObjectProperty<RequestBodyType> bodyTypeProperty() { return bodyType; }
    public StringProperty requestBodyProperty() { return requestBody; }
    public BooleanProperty loadingProperty() { return loading; }
    public BooleanProperty dirtyProperty() { return dirty; }
    public BooleanProperty persistedProperty() { return persisted; }
    public BooleanProperty sidebarExpandedProperty() { return sidebarExpanded; }
    public StringProperty selectedRequestTabProperty() { return selectedRequestTab; }
    public StringProperty selectedResponseTabProperty() { return selectedResponseTab; }
    public StringProperty responseStatusProperty() { return responseStatus; }
    public StringProperty responseDurationProperty() { return responseDuration; }
    public StringProperty responseSizeProperty() { return responseSize; }
    public StringProperty responseBodyProperty() { return responseBody; }
    public StringProperty responseHeadersProperty() { return responseHeaders; }
    public StringProperty responseRawProperty() { return responseRaw; }
    public StringProperty statusMessageProperty() { return statusMessage; }
    public StringProperty errorMessageProperty() { return errorMessage; }
    public StringProperty variableFeedbackProperty() { return variableFeedback; }
    public ObjectProperty<VariableFeedbackState> variableFeedbackStateProperty() {
        return variableFeedbackState;
    }
    public ObjectProperty<VariableResolutionStatus> variableResolutionStatusProperty() {
        return variableResolutionStatus;
    }
    public ObjectProperty<ResponsiveLayoutMode> responsiveModeProperty() { return responsiveMode; }
    public ObjectProperty<RequestLocation> requestLocationProperty() { return requestLocation; }
    public ObjectProperty<EnvironmentSelection> selectedEnvironmentProperty() { return selectedEnvironment; }
    public ObservableList<RequestCollection> collections() { return collections; }
    public ObservableList<SavedRequest> savedRequests() { return savedRequests; }
    public ObservableList<RequestHistoryEntry> history() { return history; }
    public ObservableList<RequestEnvironment> environments() { return environments; }
    public EnvironmentConfiguration environmentConfiguration() { return environmentConfiguration; }

    private record EditorSnapshot(HttpRequestDefinition definition, RequestLocation location) {
    }

    private record SendCompletion(ExecutionReport report, WorkspaceSnapshot snapshot) {
    }
}
