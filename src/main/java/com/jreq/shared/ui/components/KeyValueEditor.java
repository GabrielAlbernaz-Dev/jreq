package com.jreq.shared.ui.components;

import com.jreq.request.application.VariableResolutionStatus;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.shared.ui.KeyValueEditorModel;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

public final class KeyValueEditor extends VBox {
    private final KeyValueEditorModel model = new KeyValueEditorModel();
    private final VBox rows = new VBox();
    private final List<VariableHighlightingField> valueFields = new ArrayList<>();
    private VariableResolutionStatus resolutionStatus =
            new VariableResolutionStatus(0, List.of(), java.util.Set.of());
    private Consumer<List<KeyValueEntry>> onChange = entries -> { };

    public KeyValueEditor() {
        getStyleClass().add("key-value-editor");
        rows.getStyleClass().add("key-value-rows");

        Button addButton = new Button("+ Add item");
        addButton.getStyleClass().add("secondary-button");
        addButton.setOnAction(event -> {
            model.addEntry();
            renderRows();
            notifyChanged();
        });
        getChildren().addAll(rows, addButton);
        renderRows();
    }

    public KeyValueEditorModel model() {
        return model;
    }

    public void setEntries(List<KeyValueEntry> entries) {
        model.replaceEntries(entries);
        renderRows();
    }

    public void setOnChange(Consumer<List<KeyValueEntry>> action) {
        onChange = Objects.requireNonNull(action, "action");
    }

    public void setVariableResolutionStatus(VariableResolutionStatus status) {
        resolutionStatus = Objects.requireNonNull(status, "status");
        valueFields.forEach(field -> field.setResolutionStatus(status));
    }

    private void renderRows() {
        rows.getChildren().clear();
        valueFields.clear();
        for (KeyValueEntry entry : model.entries()) {
            rows.getChildren().add(createRow(entry));
        }
    }

    private HBox createRow(KeyValueEntry entry) {
        CheckBox enabled = new CheckBox();
        enabled.setSelected(entry.enabled());
        enabled.setAccessibleText("Enable entry");

        TextField key = new TextField(entry.key());
        key.setPromptText("Key");
        key.setAccessibleText("Entry key");
        key.getStyleClass().add("monospace");
        HBox.setHgrow(key, Priority.ALWAYS);

        VariableHighlightingField value = new VariableHighlightingField();
        value.textProperty().set(entry.value());
        value.setPromptText("Value");
        value.setAccessibleText("Entry value");
        value.getStyleClass().add("monospace");
        value.setResolutionStatus(resolutionStatus);
        valueFields.add(value);
        HBox.setHgrow(value, Priority.ALWAYS);

        Button remove = new Button("×");
        remove.getStyleClass().add("icon-button");
        remove.setAccessibleText("Remove entry");
        remove.setTooltip(new Tooltip("Remove entry"));
        remove.setOnAction(event -> {
            model.remove(entry.id());
            renderRows();
            notifyChanged();
        });

        Runnable update = () -> {
            model.update(entry.id(), key.getText(), value.getText(), enabled.isSelected());
            notifyChanged();
        };
        key.textProperty().addListener((observable, oldValue, newValue) -> update.run());
        value.textProperty().addListener((observable, oldValue, newValue) -> update.run());
        enabled.selectedProperty().addListener((observable, oldValue, newValue) -> update.run());

        HBox row = new HBox(enabled, key, value, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("key-value-row");
        return row;
    }

    private void notifyChanged() {
        onChange.accept(model.entries());
    }
}
