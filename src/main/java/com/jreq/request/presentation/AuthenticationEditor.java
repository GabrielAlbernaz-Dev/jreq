package com.jreq.request.presentation;

import com.jreq.request.application.VariableResolutionStatus;
import com.jreq.request.domain.RequestAuthentication;
import com.jreq.shared.ui.components.VariableHighlightingField;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class AuthenticationEditor extends VBox {
    private final ComboBox<AuthenticationMethod> methodSelector = new ComboBox<>();
    private final VBox fields = new VBox(10);
    private final List<VariableHighlightingField> variableFields = new ArrayList<>();

    private RequestAuthentication authentication = RequestAuthentication.none();
    private VariableResolutionStatus resolutionStatus =
            new VariableResolutionStatus(0, List.of(), java.util.Set.of());
    private Consumer<RequestAuthentication> onChange = ignored -> { };
    private boolean synchronizing;

    public AuthenticationEditor() {
        getStyleClass().add("authentication-editor");
        setSpacing(12);

        Label methodLabel = new Label("AUTH TYPE");
        methodLabel.getStyleClass().add("section-kicker");
        methodSelector.setItems(FXCollections.observableArrayList(AuthenticationMethod.values()));
        methodSelector.getSelectionModel().select(AuthenticationMethod.NONE);
        methodSelector.getStyleClass().add("authentication-method-selector");
        methodSelector.setAccessibleText("Authentication type");
        methodSelector.valueProperty().addListener((observable, oldValue, selected) -> {
            if (synchronizing || selected == null) {
                return;
            }
            authentication = selected.emptyConfiguration();
            renderFields();
            notifyChanged();
        });

        HBox methodRow = new HBox(10, methodLabel, methodSelector);
        methodRow.setAlignment(Pos.CENTER_LEFT);
        fields.getStyleClass().add("authentication-fields");

        Label precedence = helperLabel(
                "When authentication is enabled, it takes precedence over a manual Authorization header.");
        Label storage = helperLabel(
                "Credentials are masked here but stored unencrypted in your local workspace. Variables are supported.");
        storage.getStyleClass().add("authentication-storage-warning");
        getChildren().addAll(methodRow, fields, precedence, storage);
        renderFields();
    }

    public void setAuthentication(RequestAuthentication updatedAuthentication) {
        authentication = Objects.requireNonNull(updatedAuthentication, "updatedAuthentication");
        synchronizing = true;
        methodSelector.getSelectionModel().select(AuthenticationMethod.forAuthentication(authentication));
        synchronizing = false;
        renderFields();
    }

    public void setOnChange(Consumer<RequestAuthentication> action) {
        onChange = Objects.requireNonNull(action, "action");
    }

    public void setVariableResolutionStatus(VariableResolutionStatus status) {
        resolutionStatus = Objects.requireNonNull(status, "status");
        variableFields.forEach(field -> field.setResolutionStatus(status));
    }

    private void renderFields() {
        fields.getChildren().clear();
        variableFields.clear();
        switch (authentication) {
            case RequestAuthentication.None ignored -> renderNone();
            case RequestAuthentication.Basic basic -> renderBasic(basic);
            case RequestAuthentication.JwtBearer jwt -> renderJwt(jwt);
        }
    }

    private void renderNone() {
        Label title = new Label("No authentication");
        title.getStyleClass().add("panel-title");
        fields.getChildren().addAll(
                title,
                helperLabel("The request will be sent without an automatically generated Authorization header."));
    }

    private void renderBasic(RequestAuthentication.Basic basic) {
        VariableHighlightingField username = variableField(basic.username(), "Username");
        username.textProperty().addListener((observable, oldValue, newValue) -> {
            authentication = new RequestAuthentication.Basic(newValue, currentBasic().password());
            notifyChanged();
        });

        MaskedValueField password = new MaskedValueField(basic.password(), "Password", "Basic password");
        password.valueProperty().addListener((observable, oldValue, newValue) -> {
            authentication = new RequestAuthentication.Basic(currentBasic().username(), newValue);
            notifyChanged();
        });
        fields.getChildren().addAll(
                labeledField("USERNAME", username),
                labeledField("PASSWORD", password));
    }

    private void renderJwt(RequestAuthentication.JwtBearer jwt) {
        MaskedValueField token = new MaskedValueField(jwt.token(), "JWT token or {{variable}}", "JWT token");
        token.valueProperty().addListener((observable, oldValue, newValue) -> {
            authentication = new RequestAuthentication.JwtBearer(newValue);
            notifyChanged();
        });
        fields.getChildren().add(labeledField("TOKEN", token));
    }

    private VariableHighlightingField variableField(String value, String prompt) {
        VariableHighlightingField field = new VariableHighlightingField();
        field.textProperty().set(value);
        field.setPromptText(prompt);
        field.setAccessibleText(prompt);
        field.setResolutionStatus(resolutionStatus);
        field.getStyleClass().add("authentication-text-field");
        variableFields.add(field);
        return field;
    }

    private VBox labeledField(String labelText, Node field) {
        Label label = new Label(labelText);
        label.getStyleClass().add("section-kicker");
        VBox container = new VBox(5, label, field);
        container.getStyleClass().add("authentication-field-group");
        return container;
    }

    private Label helperLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-label");
        label.setWrapText(true);
        return label;
    }

    private RequestAuthentication.Basic currentBasic() {
        if (authentication instanceof RequestAuthentication.Basic basic) {
            return basic;
        }
        throw new IllegalStateException("Basic authentication is not selected.");
    }

    private void notifyChanged() {
        if (!synchronizing) {
            onChange.accept(authentication);
        }
    }

    private enum AuthenticationMethod {
        NONE("None") {
            @Override
            RequestAuthentication emptyConfiguration() {
                return RequestAuthentication.none();
            }
        },
        BASIC("Basic Auth") {
            @Override
            RequestAuthentication emptyConfiguration() {
                return new RequestAuthentication.Basic("", "");
            }
        },
        JWT_BEARER("JWT Bearer") {
            @Override
            RequestAuthentication emptyConfiguration() {
                return new RequestAuthentication.JwtBearer("");
            }
        };

        private final String label;

        AuthenticationMethod(String label) {
            this.label = label;
        }

        abstract RequestAuthentication emptyConfiguration();

        static AuthenticationMethod forAuthentication(RequestAuthentication authentication) {
            return switch (authentication) {
                case RequestAuthentication.None ignored -> NONE;
                case RequestAuthentication.Basic ignored -> BASIC;
                case RequestAuthentication.JwtBearer ignored -> JWT_BEARER;
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class MaskedValueField extends HBox {
        private final StringProperty value = new SimpleStringProperty("");

        private MaskedValueField(String initialValue, String prompt, String accessibleName) {
            setSpacing(8);
            setAlignment(Pos.CENTER_LEFT);
            getStyleClass().add("masked-authentication-field");

            PasswordField masked = new PasswordField();
            masked.setPromptText(prompt);
            masked.setAccessibleText(accessibleName);
            masked.getStyleClass().add("monospace");
            TextField revealed = new TextField();
            revealed.setPromptText(prompt);
            revealed.setAccessibleText(accessibleName);
            revealed.getStyleClass().add("monospace");
            value.set(Objects.requireNonNull(initialValue, "initialValue"));
            masked.textProperty().bindBidirectional(value);
            revealed.textProperty().bindBidirectional(value);

            ToggleButton reveal = new ToggleButton("Show");
            reveal.getStyleClass().add("quiet-action");
            reveal.setAccessibleText("Reveal " + accessibleName.toLowerCase());
            reveal.setTooltip(new Tooltip("Reveal or mask this value"));
            revealed.visibleProperty().bind(reveal.selectedProperty());
            revealed.managedProperty().bind(revealed.visibleProperty());
            masked.visibleProperty().bind(reveal.selectedProperty().not());
            masked.managedProperty().bind(masked.visibleProperty());
            reveal.selectedProperty().addListener((observable, oldValue, selected) ->
                    reveal.setText(selected ? "Hide" : "Show"));

            StackPane input = new StackPane(masked, revealed);
            input.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(input, Priority.ALWAYS);
            getChildren().addAll(input, reveal);
        }

        private StringProperty valueProperty() {
            return value;
        }
    }
}
