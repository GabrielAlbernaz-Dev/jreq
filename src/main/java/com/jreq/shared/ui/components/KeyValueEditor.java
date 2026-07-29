package com.jreq.shared.ui.components;

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
import java.util.Objects;
import java.util.function.Consumer;

public final class KeyValueEditor extends VBox {
    private final KeyValueEditorModel model = new KeyValueEditorModel();
    private final VBox rows = new VBox();
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

    private void renderRows() {
        rows.getChildren().clear();
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

        TextField value = new TextField(entry.value());
        value.setPromptText("Value");
        value.setAccessibleText("Entry value");
        value.getStyleClass().add("monospace");
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
