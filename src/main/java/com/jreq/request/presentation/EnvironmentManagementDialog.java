package com.jreq.request.presentation;

import com.jreq.request.application.EnvironmentConfiguration;
import com.jreq.request.domain.EnvironmentScope;
import com.jreq.request.domain.EnvironmentVariable;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.domain.RequestLocation;
import com.jreq.shared.ui.DialogButtons;
import com.jreq.shared.ui.ResponsiveLayoutMode;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import javafx.stage.Window;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class EnvironmentManagementDialog {
    private final Window owner;
    private final List<RequestCollection> collections;
    private final RequestLocation currentLocation;
    private final ResponsiveLayoutMode layoutMode;
    private final DraftConfiguration draft;
    private final ListView<EditorTarget> targets = new ListView<>();
    private final BorderPane editor = new BorderPane();
    private final Label validationMessage = new Label();

    EnvironmentManagementDialog(
            Window owner,
            EnvironmentConfiguration configuration,
            List<RequestCollection> collections,
            RequestLocation currentLocation,
            ResponsiveLayoutMode layoutMode
    ) {
        this.owner = owner;
        this.collections = List.copyOf(Objects.requireNonNull(collections, "collections"));
        this.currentLocation = Objects.requireNonNull(currentLocation, "currentLocation");
        this.layoutMode = Objects.requireNonNull(layoutMode, "layoutMode");
        this.draft = DraftConfiguration.from(Objects.requireNonNull(configuration, "configuration"));
    }

    Optional<EnvironmentConfiguration> show() {
        Dialog<EnvironmentConfiguration> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("jREQ — Manage environments");
        dialog.setHeaderText("Environments and global variables");
        dialog.getDialogPane().getStyleClass().add("environment-dialog");
        dialog.getDialogPane().setContent(content());

        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, DialogButtons.cancel());
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String error = validateDraft();
            if (!error.isEmpty()) {
                validationMessage.setText(error);
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button.equals(save) ? draft.toConfiguration() : null);
        dialog.getDialogPane().getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/css/theme.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/components.css")).toExternalForm());
        configureInitialSize(dialog);
        refreshTargets();
        selectInitialTarget();
        return dialog.showAndWait();
    }

    private void configureInitialSize(Dialog<?> dialog) {
        dialog.setResizable(true);
        double preferredWidth = switch (layoutMode) {
            case COMPACT -> 720;
            case NORMAL -> 960;
            case WIDE -> 1_080;
        };
        double preferredHeight = layoutMode == ResponsiveLayoutMode.COMPACT ? 720 : 640;
        if (owner != null && owner.getWidth() > 0 && owner.getHeight() > 0) {
            preferredWidth = Math.min(preferredWidth, owner.getWidth() * 0.94);
            preferredHeight = Math.min(preferredHeight, owner.getHeight() * 0.90);
        }
        dialog.getDialogPane().setPrefSize(preferredWidth, preferredHeight);
    }

    private Node content() {
        Button addEnvironment = new Button("New environment");
        addEnvironment.getStyleClass().add("secondary-button");
        addEnvironment.setMaxWidth(Double.MAX_VALUE);
        addEnvironment.setOnAction(event -> createEnvironment());

        Button deleteEnvironment = new Button("Delete");
        deleteEnvironment.getStyleClass().add("quiet-action");
        deleteEnvironment.disableProperty().bind(Bindings.createBooleanBinding(
                () -> targets.getSelectionModel().getSelectedItem() == null
                        || targets.getSelectionModel().getSelectedItem() instanceof GlobalsTarget,
                targets.getSelectionModel().selectedItemProperty()));
        deleteEnvironment.setOnAction(event -> deleteSelectedEnvironment());

        HBox secondaryActions = new HBox(8, addEnvironment, deleteEnvironment);
        HBox.setHgrow(addEnvironment, Priority.ALWAYS);
        VBox navigation = new VBox(8,
                new Label("SCOPES"), targets, secondaryActions);
        navigation.getStyleClass().add("environment-navigation");
        VBox.setVgrow(targets, Priority.ALWAYS);

        targets.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(EditorTarget target, boolean empty) {
                super.updateItem(target, empty);
                setText(empty || target == null ? "" : target.label(collections));
            }
        });
        targets.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selected) -> renderEditor(selected));

        validationMessage.getStyleClass().add("validation-message");
        validationMessage.setWrapText(true);
        validationMessage.setManaged(false);
        validationMessage.visibleProperty().bind(validationMessage.textProperty().isNotEmpty());
        validationMessage.managedProperty().bind(validationMessage.visibleProperty());

        VBox editorArea = new VBox(8, editor, validationMessage);
        editorArea.getStyleClass().add("environment-editor-area");
        VBox.setVgrow(editor, Priority.ALWAYS);
        SplitPane split = new SplitPane(navigation, editorArea);
        split.setOrientation(layoutMode == ResponsiveLayoutMode.COMPACT
                ? Orientation.VERTICAL
                : Orientation.HORIZONTAL);
        split.setDividerPositions(layoutMode == ResponsiveLayoutMode.COMPACT ? 0.24 : 0.23);
        split.getStyleClass().add("environment-split");
        return split;
    }

    private void refreshTargets() {
        EditorTarget selected = targets.getSelectionModel().getSelectedItem();
        List<EditorTarget> items = new ArrayList<>();
        items.add(new GlobalsTarget());
        draft.environments.stream()
                .sorted((left, right) -> left.name.compareToIgnoreCase(right.name))
                .map(EnvironmentTarget::new)
                .forEach(items::add);
        targets.setItems(FXCollections.observableArrayList(items));
        if (selected instanceof EnvironmentTarget oldTarget) {
            items.stream()
                    .filter(item -> item instanceof EnvironmentTarget target
                            && target.environment.id.equals(oldTarget.environment.id))
                    .findFirst()
                    .ifPresentOrElse(item -> targets.getSelectionModel().select(item),
                            () -> targets.getSelectionModel().selectFirst());
        } else {
            targets.getSelectionModel().selectFirst();
        }
    }

    private void selectInitialTarget() {
        if (currentLocation instanceof RequestLocation.Collection collection) {
            targets.getItems().stream()
                    .filter(item -> item instanceof EnvironmentTarget target
                            && target.environment.scope instanceof EnvironmentScope.Collection scope
                            && scope.collectionId().equals(collection.collectionId()))
                    .findFirst()
                    .ifPresent(item -> targets.getSelectionModel().select(item));
        }
    }

    private void renderEditor(EditorTarget target) {
        editor.setTop(null);
        editor.setCenter(null);
        editor.setBottom(null);
        validationMessage.setText("");
        if (target == null) {
            return;
        }
        List<DraftVariable> variables;
        String title;
        if (target instanceof GlobalsTarget) {
            variables = draft.globals;
            title = "Globals";
            editor.setTop(editorHeading(title, "Used when Globals only is selected."));
        } else {
            DraftEnvironment environment = ((EnvironmentTarget) target).environment;
            variables = environment.variables;
            TextField name = new TextField(environment.name);
            name.setPromptText("Environment name");
            name.textProperty().addListener((observable, oldValue, newValue) -> {
                environment.name = newValue;
                targets.refresh();
            });
            Label scope = new Label(scopeLabel(environment.scope));
            scope.getStyleClass().add("muted-label");
            VBox heading = new VBox(5, new Label("Environment"), name, scope);
            heading.getStyleClass().add("environment-editor-heading");
            editor.setTop(heading);
        }
        editor.setCenter(variableEditor(variables));
    }

    private Node editorHeading(String title, String description) {
        Label heading = new Label(title);
        heading.getStyleClass().add("panel-title");
        Label detail = new Label(description);
        detail.getStyleClass().add("muted-label");
        detail.setWrapText(true);
        VBox box = new VBox(4, heading, detail);
        box.getStyleClass().add("environment-editor-heading");
        return box;
    }

    private Node variableEditor(List<DraftVariable> variables) {
        VBox rows = new VBox(7);
        rows.getStyleClass().add("environment-variable-rows");
        Runnable renderRows = () -> {
            rows.getChildren().clear();
            for (DraftVariable variable : List.copyOf(variables)) {
                rows.getChildren().add(variableRow(variable, () -> {
                    variables.remove(variable);
                    renderEditor(targets.getSelectionModel().getSelectedItem());
                }));
            }
            if (variables.isEmpty()) {
                Label empty = new Label("No variables in this scope.");
                empty.getStyleClass().add("muted-label");
                rows.getChildren().add(empty);
            }
        };
        renderRows.run();

        Button add = new Button("+ Add variable");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(event -> {
            variables.add(DraftVariable.empty());
            renderEditor(targets.getSelectionModel().getSelectedItem());
        });
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("environment-variable-scroll");
        Label guidance = new Label(
                "Enabled variables participate in resolution. Secret only masks the value in this editor.");
        guidance.setWrapText(true);
        guidance.getStyleClass().add("muted-label");
        VBox box = new VBox(10, guidance, scroll, add);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return box;
    }

    private Node variableRow(DraftVariable variable, Runnable remove) {
        CheckBox enabled = new CheckBox("Enabled");
        enabled.setSelected(variable.enabled);
        enabled.selectedProperty().addListener((observable, oldValue, newValue) -> variable.enabled = newValue);

        TextField key = new TextField(variable.key);
        key.setPromptText("Variable");
        key.textProperty().addListener((observable, oldValue, newValue) -> variable.key = newValue);

        TextField plainValue = new TextField(variable.value);
        plainValue.setPromptText("Value");
        PasswordField maskedValue = new PasswordField();
        maskedValue.setText(variable.value);
        maskedValue.setPromptText("Secret value");
        AtomicBoolean synchronizingValue = new AtomicBoolean();
        plainValue.textProperty().addListener((observable, oldValue, newValue) -> {
            if (synchronizingValue.get()) {
                return;
            }
            synchronizingValue.set(true);
            variable.value = newValue;
            maskedValue.setText(newValue);
            synchronizingValue.set(false);
        });
        maskedValue.textProperty().addListener((observable, oldValue, newValue) -> {
            if (synchronizingValue.get()) {
                return;
            }
            synchronizingValue.set(true);
            variable.value = newValue;
            plainValue.setText(newValue);
            synchronizingValue.set(false);
        });

        SVGPath lockIcon = new SVGPath();
        lockIcon.setContent("M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10"
                + "c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2"
                + "s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1"
                + "s3.1 1.39 3.1 3.1v2z");
        lockIcon.getStyleClass().add("secret-icon");
        ToggleButton secret = new ToggleButton("", lockIcon);
        secret.getStyleClass().add("secret-toggle");
        secret.setSelected(variable.secret);
        secret.selectedProperty().addListener((observable, oldValue, newValue) -> variable.secret = newValue);
        Tooltip secretTooltip = new Tooltip(
                "Secret value\nMasks this value in the editor. It remains stored locally in SQLite.");
        secretTooltip.setShowDelay(Duration.millis(250));
        secret.setTooltip(secretTooltip);
        secret.setAccessibleText("Mask value as secret");
        secret.setAccessibleHelp(secretTooltip.getText());
        ToggleButton reveal = new ToggleButton("Show");
        reveal.getStyleClass().add("quiet-action");
        reveal.selectedProperty().addListener((observable, oldValue, showing) ->
                reveal.setText(showing ? "Hide" : "Show"));
        plainValue.visibleProperty().bind(secret.selectedProperty().not().or(reveal.selectedProperty()));
        plainValue.managedProperty().bind(plainValue.visibleProperty());
        maskedValue.visibleProperty().bind(secret.selectedProperty().and(reveal.selectedProperty().not()));
        maskedValue.managedProperty().bind(maskedValue.visibleProperty());
        reveal.visibleProperty().bind(secret.selectedProperty());
        reveal.managedProperty().bind(reveal.visibleProperty());
        StackPane valueFields = new StackPane(plainValue, maskedValue);
        HBox.setHgrow(valueFields, Priority.ALWAYS);

        Button delete = new Button("×");
        delete.getStyleClass().addAll("icon-button", "small-icon-button");
        delete.setAccessibleText("Delete variable");
        delete.setOnAction(event -> remove.run());
        HBox.setHgrow(key, Priority.SOMETIMES);
        HBox row = new HBox(9, enabled, key, valueFields, secret, reveal, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("environment-variable-row");
        return row;
    }

    private void createEnvironment() {
        Dialog<CreateEnvironment> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("jREQ — New environment");
        dialog.setHeaderText("Create environment");
        TextField name = new TextField();
        name.setPromptText("Environment name");
        ComboBox<ScopeOption> scope = new ComboBox<>();
        scope.setMaxWidth(Double.MAX_VALUE);
        scope.getItems().add(new ScopeOption("Global", EnvironmentScope.global()));
        collections.forEach(collection -> scope.getItems().add(new ScopeOption(
                collection.name(), EnvironmentScope.collection(collection.id()))));
        scope.getSelectionModel().select(scope.getItems().stream()
                .filter(option -> matchesCurrentLocation(option.scope))
                .findFirst().orElse(scope.getItems().getFirst()));
        VBox form = new VBox(8, new Label("Name"), name, new Label("Scope"), scope);
        dialog.getDialogPane().setContent(form);
        ButtonType create = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, DialogButtons.cancel());
        dialog.getDialogPane().lookupButton(create).disableProperty().bind(name.textProperty().isEmpty());
        dialog.setResultConverter(button -> button.equals(create)
                ? new CreateEnvironment(name.getText().strip(), scope.getValue().scope)
                : null);
        dialog.getDialogPane().getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/css/theme.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/components.css")).toExternalForm());
        dialog.showAndWait().ifPresent(created -> {
            Instant now = Instant.now();
            DraftEnvironment environment = new DraftEnvironment(
                    UUID.randomUUID(), created.name, created.scope, new ArrayList<>(), now, now);
            draft.environments.add(environment);
            refreshTargets();
            targets.getItems().stream()
                    .filter(item -> item instanceof EnvironmentTarget target
                            && target.environment.id.equals(environment.id))
                    .findFirst()
                    .ifPresent(item -> targets.getSelectionModel().select(item));
        });
    }

    private void deleteSelectedEnvironment() {
        if (targets.getSelectionModel().getSelectedItem() instanceof EnvironmentTarget selected) {
            draft.environments.remove(selected.environment);
            refreshTargets();
        }
    }

    private String validateDraft() {
        Set<String> environmentNames = new HashSet<>();
        for (DraftEnvironment environment : draft.environments) {
            if (environment.name == null || environment.name.isBlank()) {
                return "Every environment needs a name.";
            }
            String nameKey = scopeKey(environment.scope) + "\u0000"
                    + environment.name.strip().toLowerCase(Locale.ROOT);
            if (!environmentNames.add(nameKey)) {
                return "Environment names must be unique within their scope.";
            }
            String variableError = validateVariables(environment.variables);
            if (!variableError.isEmpty()) {
                return environment.name.strip() + ": " + variableError;
            }
        }
        String globalsError = validateVariables(draft.globals);
        return globalsError.isEmpty() ? "" : "Globals: " + globalsError;
    }

    private String validateVariables(List<DraftVariable> variables) {
        Set<String> keys = new HashSet<>();
        for (DraftVariable variable : variables) {
            String key = variable.key == null ? "" : variable.key.strip();
            if (key.isEmpty()) {
                return "every variable needs a key.";
            }
            if (!keys.add(key)) {
                return "variable keys are case-sensitive and must be unique.";
            }
        }
        return "";
    }

    private boolean matchesCurrentLocation(EnvironmentScope scope) {
        if (currentLocation instanceof RequestLocation.Collection location
                && scope instanceof EnvironmentScope.Collection environment) {
            return location.collectionId().equals(environment.collectionId());
        }
        return currentLocation instanceof RequestLocation.Root && scope instanceof EnvironmentScope.Global;
    }

    private String scopeLabel(EnvironmentScope scope) {
        if (scope instanceof EnvironmentScope.Global) {
            return "Global environment · available from root and every collection";
        }
        UUID collectionId = ((EnvironmentScope.Collection) scope).collectionId();
        return collections.stream().filter(collection -> collection.id().equals(collectionId))
                .map(collection -> "Collection · " + collection.name())
                .findFirst().orElse("Collection");
    }

    private String scopeKey(EnvironmentScope scope) {
        return scope instanceof EnvironmentScope.Global
                ? "GLOBAL"
                : ((EnvironmentScope.Collection) scope).collectionId().toString();
    }

    private sealed interface EditorTarget permits GlobalsTarget, EnvironmentTarget {
        String label(List<RequestCollection> collections);
    }

    private record GlobalsTarget() implements EditorTarget {
        @Override
        public String label(List<RequestCollection> collections) {
            return "GLOBALS";
        }
    }

    private record EnvironmentTarget(DraftEnvironment environment) implements EditorTarget {
        @Override
        public String label(List<RequestCollection> collections) {
            if (environment.scope instanceof EnvironmentScope.Global) {
                return "GLOBAL · " + environment.name;
            }
            UUID collectionId = ((EnvironmentScope.Collection) environment.scope).collectionId();
            String collectionName = collections.stream()
                    .filter(collection -> collection.id().equals(collectionId))
                    .map(RequestCollection::name)
                    .findFirst().orElse("Collection");
            return collectionName.toUpperCase(Locale.ROOT) + " · " + environment.name;
        }
    }

    private static final class DraftConfiguration {
        private final List<DraftVariable> globals;
        private final List<DraftEnvironment> environments;

        private DraftConfiguration(List<DraftVariable> globals, List<DraftEnvironment> environments) {
            this.globals = globals;
            this.environments = environments;
        }

        private static DraftConfiguration from(EnvironmentConfiguration configuration) {
            return new DraftConfiguration(
                    configuration.globals().stream().map(DraftVariable::from).collect(
                            java.util.stream.Collectors.toCollection(ArrayList::new)),
                    configuration.environments().stream().map(DraftEnvironment::from).collect(
                            java.util.stream.Collectors.toCollection(ArrayList::new)));
        }

        private EnvironmentConfiguration toConfiguration() {
            return new EnvironmentConfiguration(
                    buildVariables(globals),
                    environments.stream().map(DraftEnvironment::toEnvironment).toList());
        }

        private static List<EnvironmentVariable> buildVariables(List<DraftVariable> variables) {
            List<EnvironmentVariable> result = new ArrayList<>();
            for (int index = 0; index < variables.size(); index++) {
                DraftVariable variable = variables.get(index);
                result.add(new EnvironmentVariable(
                        variable.id,
                        variable.key.strip(),
                        variable.value,
                        variable.enabled,
                        variable.secret,
                        index));
            }
            return List.copyOf(result);
        }
    }

    private static final class DraftEnvironment {
        private final UUID id;
        private String name;
        private final EnvironmentScope scope;
        private final List<DraftVariable> variables;
        private final Instant createdAt;
        private final Instant updatedAt;

        private DraftEnvironment(
                UUID id,
                String name,
                EnvironmentScope scope,
                List<DraftVariable> variables,
                Instant createdAt,
                Instant updatedAt
        ) {
            this.id = id;
            this.name = name;
            this.scope = scope;
            this.variables = variables;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        private static DraftEnvironment from(RequestEnvironment environment) {
            return new DraftEnvironment(
                    environment.id(),
                    environment.name(),
                    environment.scope(),
                    environment.variables().stream().map(DraftVariable::from).collect(
                            java.util.stream.Collectors.toCollection(ArrayList::new)),
                    environment.createdAt(),
                    environment.updatedAt());
        }

        private RequestEnvironment toEnvironment() {
            return new RequestEnvironment(
                    id,
                    name.strip(),
                    scope,
                    DraftConfiguration.buildVariables(variables),
                    createdAt,
                    updatedAt);
        }
    }

    private static final class DraftVariable {
        private final UUID id;
        private String key;
        private String value;
        private boolean enabled;
        private boolean secret;

        private DraftVariable(UUID id, String key, String value, boolean enabled, boolean secret) {
            this.id = id;
            this.key = key;
            this.value = value;
            this.enabled = enabled;
            this.secret = secret;
        }

        private static DraftVariable from(EnvironmentVariable variable) {
            return new DraftVariable(
                    variable.id(), variable.key(), variable.value(), variable.enabled(), variable.secret());
        }

        private static DraftVariable empty() {
            return new DraftVariable(UUID.randomUUID(), "", "", true, false);
        }
    }

    private record ScopeOption(String label, EnvironmentScope scope) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record CreateEnvironment(String name, EnvironmentScope scope) {
    }
}
