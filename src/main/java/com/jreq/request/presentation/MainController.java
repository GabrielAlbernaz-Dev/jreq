package com.jreq.request.presentation;

import com.jreq.request.domain.RequestBodyType;
import com.jreq.request.domain.EnvironmentSelection;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestHistoryEntry;
import com.jreq.request.domain.RequestLocation;
import com.jreq.request.domain.SavedRequest;
import com.jreq.shared.ui.ErrorAlert;
import com.jreq.shared.ui.ResponsiveLayoutManager;
import com.jreq.shared.ui.components.EmptyStateView;
import com.jreq.shared.ui.components.KeyValueEditor;
import com.jreq.shared.ui.components.RequestBarControl;
import com.jreq.shared.ui.components.ResponseMetadataView;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Objects;

public final class MainController implements WorkspaceSidebar.Actions {
    private final MainViewModel viewModel;

    @FXML private BorderPane mainRoot;
    @FXML private VBox sidebar;
    @FXML private Button sidebarToggle;
    @FXML private MenuButton environmentMenu;
    @FXML private RequestBarControl requestBar;
    @FXML private TabPane requestTabs;
    @FXML private TabPane responseTabs;
    @FXML private ResponseMetadataView responseMetadata;
    @FXML private CheckBox responseFormattingToggle;
    @FXML private TextArea responseBody;
    @FXML private TextArea responseHeaders;
    @FXML private TextArea responseRaw;
    @FXML private TextArea requestBody;
    @FXML private ComboBox<RequestBodyType> bodyTypeSelector;
    @FXML private KeyValueEditor paramsEditor;
    @FXML private KeyValueEditor headersEditor;
    @FXML private Label statusMessage;
    @FXML private Label requestNameLabel;
    @FXML private Label requestLocationLabel;
    @FXML private Label variableFeedbackLabel;
    @FXML private Label dirtyIndicator;
    @FXML private EmptyStateView authEmptyState;
    @FXML private VBox rootRequests;
    @FXML private VBox collectionsList;
    @FXML private VBox historyList;

    private ResponsiveLayoutManager responsiveLayoutManager;
    private WorkspaceDialogs dialogs;
    private WorkspaceSidebar workspaceSidebar;

    public MainController(MainViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    @FXML
    private void initialize() {
        dialogs = new WorkspaceDialogs(this::owner);
        workspaceSidebar = new WorkspaceSidebar(rootRequests, collectionsList, historyList, this);
        bindEditor();
        bindResponse();
        bindNavigation();
        installListRendering();
        installEnvironmentMenu();

        authEmptyState.setTitle("Authentication is not configured");
        authEmptyState.setDescription(
                "Auth strategies will be added incrementally without storing credentials here.");
        viewModel.errorMessageProperty().addListener((observable, oldValue, message) -> {
            if (message != null && !message.isBlank()) {
                ErrorAlert.show(owner(), "Operation failed", message);
                viewModel.errorMessageProperty().set("");
            }
        });

        syncEditorsFromViewModel();
        renderSidebar();
        viewModel.initializeWorkspace();
    }

    private void bindEditor() {
        requestBar.methodProperty().bindBidirectional(viewModel.selectedMethodProperty());
        requestBar.urlProperty().bindBidirectional(viewModel.urlProperty());
        applyVariableResolutionStatus(viewModel.variableResolutionStatusProperty().get());
        viewModel.variableResolutionStatusProperty().addListener(
                (observable, oldValue, newValue) -> applyVariableResolutionStatus(newValue));
        requestBar.setOnSend(viewModel::sendRequest);
        requestBar.setOnSave(this::handleSave);
        requestBar.setOnSaveAs(this::handleSaveAs);
        viewModel.loadingProperty().addListener((observable, oldValue, loading) ->
                requestBar.setLoading(loading));

        bodyTypeSelector.setItems(FXCollections.observableArrayList(RequestBodyType.values()));
        bodyTypeSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(RequestBodyType type) {
                return switch (type) {
                    case NONE -> "None";
                    case JSON -> "JSON";
                    case RAW_TEXT -> "Raw text";
                    case null -> "";
                };
            }

            @Override
            public RequestBodyType fromString(String value) {
                throw new UnsupportedOperationException("Body type is selected from a fixed list");
            }
        });
        bodyTypeSelector.valueProperty().bindBidirectional(viewModel.bodyTypeProperty());
        requestBody.textProperty().bindBidirectional(viewModel.requestBodyProperty());
        requestBody.disableProperty().bind(viewModel.bodyTypeProperty().isEqualTo(RequestBodyType.NONE));
        paramsEditor.setOnChange(viewModel::updateQueryParameters);
        headersEditor.setOnChange(viewModel::updateHeaders);
        requestNameLabel.textProperty().bind(viewModel.requestNameProperty());
        dirtyIndicator.visibleProperty().bind(viewModel.dirtyProperty());
        dirtyIndicator.managedProperty().bind(viewModel.dirtyProperty());
        requestLocationLabel.textProperty().bind(Bindings.createStringBinding(
                () -> locationName(viewModel.requestLocationProperty().get()),
                viewModel.requestLocationProperty(), viewModel.collections()));
        variableFeedbackLabel.textProperty().bind(viewModel.variableFeedbackProperty());
        variableFeedbackLabel.visibleProperty().bind(viewModel.variableFeedbackProperty().isNotEmpty());
        variableFeedbackLabel.managedProperty().bind(variableFeedbackLabel.visibleProperty());
        Tooltip variableFeedbackTooltip = new Tooltip();
        variableFeedbackTooltip.textProperty().bind(viewModel.variableFeedbackProperty());
        variableFeedbackLabel.setTooltip(variableFeedbackTooltip);
        viewModel.variableFeedbackStateProperty().addListener(
                (observable, oldValue, newValue) -> applyVariableFeedbackStyle(newValue));
        applyVariableFeedbackStyle(viewModel.variableFeedbackStateProperty().get());
    }

    private void applyVariableResolutionStatus(
            com.jreq.request.application.VariableResolutionStatus status
    ) {
        requestBar.setVariableResolutionStatus(status);
        paramsEditor.setVariableResolutionStatus(status);
        headersEditor.setVariableResolutionStatus(status);
    }

    private void applyVariableFeedbackStyle(VariableFeedbackState state) {
        variableFeedbackLabel.getStyleClass().removeAll(
                "variable-feedback-resolved", "variable-feedback-invalid");
        if (state == VariableFeedbackState.RESOLVED) {
            variableFeedbackLabel.getStyleClass().add("variable-feedback-resolved");
        } else if (state == VariableFeedbackState.INVALID) {
            variableFeedbackLabel.getStyleClass().add("variable-feedback-invalid");
        }
    }

    private void bindResponse() {
        responseMetadata.statusProperty().bind(viewModel.responseStatusProperty());
        responseMetadata.durationProperty().bind(viewModel.responseDurationProperty());
        responseMetadata.sizeProperty().bind(viewModel.responseSizeProperty());
        responseFormattingToggle.selectedProperty()
                .bindBidirectional(viewModel.responseFormattingEnabledProperty());
        responseFormattingToggle.disableProperty()
                .bind(viewModel.responseFormattingAvailableProperty().not());
        responseBody.textProperty().bind(viewModel.responseBodyProperty());
        responseHeaders.textProperty().bind(viewModel.responseHeadersProperty());
        responseRaw.textProperty().bind(viewModel.responseRawProperty());
        statusMessage.textProperty().bind(viewModel.statusMessageProperty());
    }

    private void bindNavigation() {
        sidebar.visibleProperty().bind(viewModel.sidebarExpandedProperty());
        sidebar.managedProperty().bind(viewModel.sidebarExpandedProperty());
        sidebarToggle.setOnAction(event -> viewModel.toggleSidebar());
        requestTabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) ->
                viewModel.selectedRequestTabProperty().set(tabText(newTab)));
        responseTabs.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) ->
                viewModel.selectedResponseTabProperty().set(tabText(newTab)));
    }

    private void installListRendering() {
        viewModel.collections().addListener((ListChangeListener<RequestCollection>) change -> renderSidebar());
        viewModel.savedRequests().addListener((ListChangeListener<SavedRequest>) change -> renderSidebar());
        viewModel.history().addListener((ListChangeListener<RequestHistoryEntry>) change -> renderSidebar());
    }

    private void installEnvironmentMenu() {
        viewModel.environments().addListener(
                (ListChangeListener<RequestEnvironment>) change -> renderEnvironmentMenu());
        viewModel.requestLocationProperty().addListener(
                (observable, oldValue, newValue) -> renderEnvironmentMenu());
        viewModel.selectedEnvironmentProperty().addListener(
                (observable, oldValue, newValue) -> renderEnvironmentMenu());
        environmentMenu.disableProperty().bind(viewModel.loadingProperty());
        renderEnvironmentMenu();
    }

    private void renderEnvironmentMenu() {
        environmentMenu.getItems().clear();
        EnvironmentMenuModel menu = EnvironmentMenuModel.from(
                viewModel.environmentConfiguration(),
                viewModel.collections());
        MenuItem globalsOnly = new MenuItem(menu.globalsOnlyLabel());
        globalsOnly.setOnAction(event -> viewModel.selectEnvironment(EnvironmentSelection.none()));
        environmentMenu.getItems().add(globalsOnly);

        environmentMenu.getItems().add(new SeparatorMenuItem());
        if (menu.groups().isEmpty()) {
            environmentMenu.getItems().add(disabledEnvironmentItem(
                    menu.emptyMessage(), "environment-menu-message"));
        } else {
            for (int index = 0; index < menu.groups().size(); index++) {
                EnvironmentMenuModel.Group group = menu.groups().get(index);
                environmentMenu.getItems().add(disabledEnvironmentItem(
                        group.label(), "environment-menu-section"));
                group.entries().stream()
                        .map(this::selectableEnvironmentItem)
                        .forEach(environmentMenu.getItems()::add);
                if (index < menu.groups().size() - 1) {
                    environmentMenu.getItems().add(new SeparatorMenuItem());
                }
            }
        }

        environmentMenu.getItems().add(new SeparatorMenuItem());
        MenuItem manage = new MenuItem("Manage environments…");
        manage.setOnAction(event -> dialogs.showEnvironmentManager(
                        viewModel.environmentConfiguration(),
                        viewModel.collections(),
                        viewModel.requestLocationProperty().get(),
                        viewModel.responsiveModeProperty().get())
                .ifPresent(viewModel::saveEnvironmentConfiguration));
        environmentMenu.getItems().add(manage);

        environmentMenu.setText(selectedEnvironmentName());
    }

    private MenuItem selectableEnvironmentItem(EnvironmentMenuModel.Entry entry) {
        MenuItem item = new MenuItem(entry.label());
        item.setOnAction(event -> viewModel.selectEnvironment(
                EnvironmentSelection.selected(entry.environment().id())));
        return item;
    }

    private MenuItem disabledEnvironmentItem(String text, String styleClass) {
        MenuItem item = new MenuItem(text);
        item.getStyleClass().add(styleClass);
        item.setDisable(true);
        return item;
    }

    private String selectedEnvironmentName() {
        if (viewModel.selectedEnvironmentProperty().get() instanceof EnvironmentSelection.Selected selected) {
            return viewModel.environmentConfiguration().findEnvironment(selected.environmentId())
                    .map(RequestEnvironment::name)
                    .orElse("Globals only");
        }
        return "Globals only";
    }

    public void installSceneBehavior(Scene scene) {
        responsiveLayoutManager = new ResponsiveLayoutManager(mainRoot, viewModel.sidebarExpandedProperty());
        responsiveLayoutManager.modeProperty().addListener((observable, oldMode, newMode) ->
                viewModel.responsiveModeProperty().set(newMode));
        responsiveLayoutManager.attach(scene);
        scene.getAccelerators().put(shortcut(KeyCode.ENTER), viewModel::sendRequest);
        scene.getAccelerators().put(shortcut(KeyCode.B), viewModel::toggleSidebar);
        scene.getAccelerators().put(shortcut(KeyCode.S), this::handleSave);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                this::handleSaveAs);
    }

    @FXML
    private void handleNewRequest() {
        navigateWithGuard(() -> {
            viewModel.newRequest();
            syncEditorsFromViewModel();
            requestBar.requestUrlFocus();
        });
    }

    @FXML
    private void handleNewCollection() {
        dialogs.promptName("New collection", "Collection name", "")
                .ifPresent(viewModel::createCollection);
    }

    @FXML
    private void handleClearHistory() {
        if (dialogs.confirm("Clear history?", "All recorded requests and responses will be removed.")) {
            viewModel.clearHistory();
        }
    }

    private void handleSave() {
        if (viewModel.persistedProperty().get()) {
            viewModel.saveCurrent();
        } else {
            promptSave("Save request", false);
        }
    }

    private void handleSaveAs() {
        promptSave("Save request as", true);
    }

    private void promptSave(String title, boolean saveAs) {
        dialogs.showSave(title, viewModel.requestNameProperty().get(),
                        viewModel.requestLocationProperty().get(), viewModel.collections())
                .ifPresent(target -> {
                    if (saveAs) {
                        viewModel.saveAs(target.name(), target.location());
                    } else {
                        viewModel.saveNew(target.name(), target.location());
                    }
                });
    }

    private void navigateWithGuard(Runnable navigation) {
        if (!viewModel.dirtyProperty().get()) {
            navigation.run();
            return;
        }
        switch (dialogs.confirmUnsavedChanges()) {
            case DISCARD -> navigation.run();
            case SAVE -> saveBeforeNavigation(navigation);
            case CANCEL -> { }
        }
    }

    private void saveBeforeNavigation(Runnable navigation) {
        if (viewModel.persistedProperty().get()) {
            viewModel.saveCurrent().thenRun(navigation);
            return;
        }
        dialogs.showSave("Save request", viewModel.requestNameProperty().get(),
                        viewModel.requestLocationProperty().get(), viewModel.collections())
                .ifPresent(target -> viewModel.saveNew(target.name(), target.location()).thenRun(navigation));
    }

    @Override
    public List<RequestCollection> collections() {
        return List.copyOf(viewModel.collections());
    }

    @Override
    public void openRequest(SavedRequest request) {
        navigateWithGuard(() -> {
            viewModel.openSavedRequest(request);
            syncEditorsFromViewModel();
        });
    }

    @Override
    public void renameRequest(SavedRequest request) {
        dialogs.promptName("Rename request", "Request name", request.definition().name())
                .ifPresent(name -> viewModel.renameRequest(request, name));
    }

    @Override
    public void moveRequest(SavedRequest request, RequestLocation location) {
        viewModel.moveRequest(request, location);
    }

    @Override
    public void duplicateRequest(SavedRequest request) {
        dialogs.showSave("Duplicate request", request.definition().name() + " Copy",
                        request.location(), viewModel.collections())
                .ifPresent(target -> viewModel.duplicateRequest(request, target.name(), target.location()));
    }

    @Override
    public void deleteRequest(SavedRequest request) {
        if (dialogs.confirm("Delete request?", request.definition().name())) {
            viewModel.deleteRequest(request);
        }
    }

    @Override
    public void renameCollection(RequestCollection collection) {
        dialogs.promptName("Rename collection", "Collection name", collection.name())
                .ifPresent(name -> viewModel.renameCollection(collection, name));
    }

    @Override
    public void deleteCollection(RequestCollection collection) {
        dialogs.confirmCollectionDeletion(collection).ifPresent(deleteRequests -> {
            Runnable deletion = () -> viewModel.deleteCollection(collection, deleteRequests);
            if (isEditingCollection(collection) && viewModel.dirtyProperty().get()) {
                navigateWithGuard(deletion);
            } else {
                deletion.run();
            }
        });
    }

    @Override
    public void openHistory(RequestHistoryEntry entry) {
        navigateWithGuard(() -> {
            viewModel.openHistory(entry);
            syncEditorsFromViewModel();
        });
    }

    @Override
    public void deleteHistory(RequestHistoryEntry entry) {
        if (dialogs.confirm("Delete history entry?", entry.name())) {
            viewModel.deleteHistory(entry);
        }
    }

    private void renderSidebar() {
        workspaceSidebar.render(viewModel.collections(), viewModel.savedRequests(), viewModel.history());
    }

    private void syncEditorsFromViewModel() {
        paramsEditor.setEntries(viewModel.queryParameters());
        headersEditor.setEntries(viewModel.headers());
    }

    private boolean isEditingCollection(RequestCollection collection) {
        return viewModel.requestLocationProperty().get() instanceof RequestLocation.Collection location
                && location.collectionId().equals(collection.id());
    }

    private String locationName(RequestLocation location) {
        if (location instanceof RequestLocation.Collection selected) {
            return viewModel.collections().stream()
                    .filter(collection -> collection.id().equals(selected.collectionId()))
                    .map(collection -> collection.name().toUpperCase())
                    .findFirst().orElse("COLLECTION");
        }
        return "ROOT";
    }

    private KeyCodeCombination shortcut(KeyCode keyCode) {
        return new KeyCodeCombination(keyCode, KeyCombination.SHORTCUT_DOWN);
    }

    private Window owner() {
        return mainRoot.getScene() == null ? null : mainRoot.getScene().getWindow();
    }

    private String tabText(Tab tab) {
        return tab == null ? "" : tab.getText();
    }
}
