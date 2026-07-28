package com.jreq.shared.ui.components;

import com.jreq.request.domain.HttpMethod;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public final class RequestBarControl extends HBox {
    private final ComboBox<HttpMethod> methodSelector = new ComboBox<>();
    private final TextField urlField = new TextField();
    private final Button sendButton = new Button("Send");

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

        getChildren().addAll(methodSelector, urlField, sendButton);
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

    public void setLoading(boolean loading) {
        sendButton.setDisable(loading);
        sendButton.setText(loading ? "Sending…" : "Send");
    }

    public void requestUrlFocus() {
        urlField.requestFocus();
        urlField.selectAll();
    }
}
