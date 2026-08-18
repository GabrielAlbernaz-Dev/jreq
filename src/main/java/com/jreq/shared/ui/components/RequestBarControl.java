package com.jreq.shared.ui.components;

import com.jreq.request.domain.HttpMethod;
import com.jreq.request.application.VariableResolutionStatus;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public final class RequestBarControl extends HBox {
    private static final PseudoClass COOKIE_JAR_OFF = PseudoClass.getPseudoClass("jar-off");

    private final ComboBox<HttpMethod> methodSelector = new ComboBox<>();
    private final VariableHighlightingField urlField = new VariableHighlightingField();
    private final Button sendButton = new Button("Send");
    private final MenuButton cookiesMenu = new MenuButton();
    private final MenuItem statusItem = new MenuItem();
    private final MenuItem manageCookiesItem = new MenuItem("Manage cookies…");
    private final MenuItem toggleJarItem = new MenuItem();
    private final BooleanProperty cookieJarEnabled = new SimpleBooleanProperty(true);
    private final SplitMenuButton saveButton = new SplitMenuButton();
    private final MenuItem saveAsItem = new MenuItem("Save As…");
    private int cookieCount;

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

        statusItem.setDisable(true);
        statusItem.getStyleClass().add("cookies-menu-status");
        cookiesMenu.getItems().addAll(
                statusItem,
                new SeparatorMenuItem(),
                manageCookiesItem,
                toggleJarItem);
        cookiesMenu.getStyleClass().add("cookies-menu");
        cookiesMenu.setAccessibleText("Cookies");
        cookieJarEnabled.addListener((observable, oldValue, enabled) -> refreshCookiesPresentation());
        toggleJarItem.setOnAction(event -> cookieJarEnabled.set(!cookieJarEnabled.get()));
        refreshCookiesPresentation();

        saveButton.setText("Save");
        saveButton.getItems().add(saveAsItem);
        saveButton.getStyleClass().add("save-button");
        saveButton.setAccessibleText("Save request");

        getChildren().addAll(methodSelector, urlField, cookiesMenu, saveButton, sendButton);
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

    public void setOnManageCookies(Runnable action) {
        manageCookiesItem.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
    }

    public BooleanProperty cookieJarEnabledProperty() {
        return cookieJarEnabled;
    }

    public void setCookieCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Cookie count cannot be negative");
        }
        cookieCount = count;
        refreshCookiesPresentation();
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

    private void refreshCookiesPresentation() {
        boolean enabled = cookieJarEnabled.get();
        cookiesMenu.pseudoClassStateChanged(COOKIE_JAR_OFF, !enabled);
        if (!enabled) {
            cookiesMenu.setText("Cookies");
            statusItem.setText("Cookie jar: off for this request");
            toggleJarItem.setText("Enable cookie jar");
            cookiesMenu.setAccessibleText("Cookies menu, jar disabled for this request");
            return;
        }
        cookiesMenu.setText(cookieCount == 0 ? "Cookies" : "Cookies · " + cookieCount);
        statusItem.setText(cookieCount == 0
                ? "Cookie jar: on · no matching cookies"
                : "Cookie jar: on · " + cookieCount + " matching");
        toggleJarItem.setText("Disable cookie jar");
        cookiesMenu.setAccessibleText(cookieCount == 0
                ? "Cookies menu, jar enabled"
                : "Cookies menu, jar enabled, " + cookieCount + " matching cookies");
    }
}
