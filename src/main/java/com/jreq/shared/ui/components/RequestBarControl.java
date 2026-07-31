package com.jreq.shared.ui.components;

import com.jreq.request.domain.HttpMethod;
import com.jreq.request.application.VariableResolutionStatus;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public final class RequestBarControl extends HBox {
    private final ComboBox<HttpMethod> methodSelector = new ComboBox<>();
    private final VariableHighlightingField urlField = new VariableHighlightingField();
    private final Button sendButton = new Button("Send");
    private final SplitMenuButton saveButton = new SplitMenuButton();
    private final MenuItem saveAsItem = new MenuItem("Save As…");

    public RequestBarControl() {
        getStyleClass().add("request-bar");
        setAlignment(Pos.CENTER_LEFT);

        methodSelector.setItems(FXCollections.observableArrayList(HttpMethod.values()));
        methodSelector.getStyleClass().add("method-selector");
        methodSelector.setAccessibleText("HTTP method");
        methodSelector.setMaxWidth(120);
        methodSelector.setPrefWidth(108);

        urlField.getStyleClass().addAll("url-field", "monospace");
        urlField.setPromptText("Enter request URL");
        urlField.setAccessibleText("Request URL");
        urlField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(urlField, Priority.ALWAYS);

        sendButton.getStyleClass().addAll("primary-button", "send-button");
        sendButton.setAccessibleText("Send request");

        saveButton.setText("Save");
        saveButton.getItems().add(saveAsItem);
        saveButton.getStyleClass().add("save-button");
        saveButton.setAccessibleText("Save request");

        getChildren().addAll(methodSelector, urlField, saveButton, sendButton);
    }

    public ObjectProperty<HttpMethod> methodProperty() {
        return methodSelector.valueProperty();
    }

    public StringProperty urlProperty() {
        return urlField.textProperty();
    }

    public void setOnSend(Runnable action) {
        sendButton.setOnAction(event -> action.run());
    }

    public void setOnSave(Runnable action) {
        saveButton.setOnAction(event -> action.run());
    }

    public void setOnSaveAs(Runnable action) {
        saveAsItem.setOnAction(event -> action.run());
    }

    public void setLoading(boolean loading) {
        sendButton.setDisable(loading);
        sendButton.setText(loading ? "Sending…" : "Send");
    }

    public void requestUrlFocus() {
        urlField.requestEditorFocus();
    }

    public void setVariableResolutionStatus(VariableResolutionStatus status) {
        urlField.setResolutionStatus(status);
    }
}
