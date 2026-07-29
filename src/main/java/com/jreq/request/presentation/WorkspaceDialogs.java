package com.jreq.request.presentation;

import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestLocation;
import javafx.scene.Node;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

final class WorkspaceDialogs {
    private final Supplier<Window> ownerSupplier;

    WorkspaceDialogs(Supplier<Window> ownerSupplier) {
        this.ownerSupplier = Objects.requireNonNull(ownerSupplier, "ownerSupplier");
    }

    Optional<SaveTarget> showSave(
            String title,
            String initialName,
            RequestLocation initialLocation,
            List<RequestCollection> collections
    ) {
        Dialog<SaveTarget> dialog = new Dialog<>();
        dialog.initOwner(owner());
        dialog.setTitle("jREQ — " + title);
        dialog.setHeaderText(title);
        TextField name = new TextField(initialName);
        name.setPromptText("Request name");
        ComboBox<LocationOption> destination = destinations(collections, initialLocation);

        GridPane content = new GridPane();
        content.setHgap(10);
        content.setVgap(10);
        content.addRow(0, new Label("Name"), name);
        content.addRow(1, new Label("Location"), destination);
        GridPane.setHgrow(name, Priority.ALWAYS);
        GridPane.setHgrow(destination, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> name.getText().isBlank(), name.textProperty()));
        dialog.setResultConverter(button -> button.equals(save)
                ? new SaveTarget(name.getText().strip(), destination.getValue().location())
                : null);
        style(dialog.getDialogPane());
        return dialog.showAndWait();
    }

    Optional<String> promptName(String title, String header, String initialValue) {
        TextInputDialog dialog = new TextInputDialog(initialValue);
        dialog.initOwner(owner());
        dialog.setTitle("jREQ — " + title);
        dialog.setHeaderText(header);
        dialog.setContentText("Name");
        style(dialog.getDialogPane());
        return dialog.showAndWait().map(String::strip).filter(value -> !value.isEmpty());
    }

    boolean confirm(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(owner());
        alert.setTitle("jREQ — Confirm");
        alert.setHeaderText(header);
        style(alert.getDialogPane());
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    Optional<Boolean> confirmCollectionDeletion(RequestCollection collection) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner());
        dialog.setTitle("jREQ — Delete collection");
        dialog.setHeaderText("Delete “" + collection.name() + "”?");
        CheckBox deleteRequests = new CheckBox("Delete contained requests");
        Label explanation = new Label(
                "Leave this unchecked to move contained requests to the root.");
        explanation.setWrapText(true);
        explanation.getStyleClass().add("muted-label");
        dialog.getDialogPane().setContent(new VBox(10, explanation, deleteRequests));
        ButtonType delete = new ButtonType("Delete collection", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(delete, ButtonType.CANCEL);
        style(dialog.getDialogPane());
        return dialog.showAndWait().filter(delete::equals).map(ignored -> deleteRequests.isSelected());
    }

    UnsavedChoice confirmUnsavedChanges() {
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Save your changes before leaving this request?", save, discard, ButtonType.CANCEL);
        alert.initOwner(owner());
        alert.setTitle("jREQ — Unsaved changes");
        alert.setHeaderText("Unsaved changes");
        style(alert.getDialogPane());
        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.filter(save::equals).isPresent()) {
            return UnsavedChoice.SAVE;
        }
        if (choice.filter(discard::equals).isPresent()) {
            return UnsavedChoice.DISCARD;
        }
        return UnsavedChoice.CANCEL;
    }

    private ComboBox<LocationOption> destinations(
            List<RequestCollection> collections,
            RequestLocation initialLocation
    ) {
        ComboBox<LocationOption> destination = new ComboBox<>();
        destination.setMaxWidth(Double.MAX_VALUE);
        destination.getItems().add(new LocationOption("Root", RequestLocation.root()));
        for (RequestCollection collection : collections) {
            destination.getItems().add(new LocationOption(
                    collection.name(), RequestLocation.collection(collection.id())));
        }
        destination.getSelectionModel().select(destination.getItems().stream()
                .filter(option -> option.location().equals(initialLocation))
                .findFirst().orElse(destination.getItems().getFirst()));
        return destination;
    }

    private Window owner() {
        return ownerSupplier.get();
    }

    private void style(DialogPane dialogPane) {
        dialogPane.getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/css/theme.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/components.css")).toExternalForm());
    }

    enum UnsavedChoice {
        SAVE,
        DISCARD,
        CANCEL
    }

    record SaveTarget(String name, RequestLocation location) {
    }

    private record LocationOption(String label, RequestLocation location) {
        @Override
        public String toString() {
            return label;
        }
    }
}
