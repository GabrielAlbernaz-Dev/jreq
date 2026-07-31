package com.jreq.shared.ui.components;

import com.jreq.request.application.VariableResolutionStatus;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VariableHighlightingField extends StackPane {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");
    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");

    private final CodeArea editor = new CodeArea();
    private final Label placeholder = new Label();
    private final StringProperty text = new SimpleStringProperty("");
    private VariableResolutionStatus resolutionStatus =
            new VariableResolutionStatus(0, List.of(), Set.of());
    private boolean synchronizing;

    public VariableHighlightingField() {
        getStyleClass().add("variable-highlighting-field");
        setAccessibleRole(AccessibleRole.TEXT_FIELD);

        editor.getStyleClass().add("variable-highlighting-editor");
        editor.setWrapText(false);
        placeholder.getStyleClass().add("placeholder");
        editor.setPlaceholder(placeholder);
        editor.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!synchronizing) {
                synchronizing = true;
                text.set(sanitize(newValue));
                synchronizing = false;
                normalizeEditorText();
            }
            applyHighlighting();
        });
        text.addListener((observable, oldValue, newValue) -> {
            if (synchronizing || editor.getText().equals(newValue)) {
                return;
            }
            synchronizing = true;
            editor.replaceText(sanitize(newValue));
            editor.moveTo(editor.getLength());
            synchronizing = false;
            applyHighlighting();
        });
        editor.focusedProperty().addListener((observable, oldValue, focused) ->
                pseudoClassStateChanged(FOCUSED, focused));
        editor.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                event.consume();
            }
        });

        VirtualizedScrollPane<CodeArea> scrollPane = new VirtualizedScrollPane<>(editor);
        scrollPane.getStyleClass().add("variable-highlighting-scroll");
        getChildren().add(scrollPane);
    }

    public StringProperty textProperty() {
        return text;
    }

    public String getText() {
        return text.get();
    }

    public void setResolutionStatus(VariableResolutionStatus status) {
        resolutionStatus = Objects.requireNonNull(status, "status");
        applyHighlighting();
    }

    public void setPromptText(String promptText) {
        placeholder.setText(Objects.requireNonNullElse(promptText, ""));
    }

    public void requestEditorFocus() {
        editor.requestFocus();
        editor.selectAll();
    }

    private void applyHighlighting() {
        String value = editor.getText();
        if (value.isEmpty()) {
            return;
        }
        editor.setStyleClass(0, value.length(), "variable-token-default");
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            String key = matcher.group(1).strip();
            String styleClass = resolutionStatus.isReferenceResolved(key)
                    ? "variable-token-resolved"
                    : "variable-token-invalid";
            editor.setStyleClass(matcher.start(), matcher.end(), styleClass);
        }
    }

    private String sanitize(String value) {
        return Objects.requireNonNullElse(value, "").replace('\n', ' ').replace('\r', ' ');
    }

    private void normalizeEditorText() {
        if (editor.getText().equals(text.get())) {
            return;
        }
        Platform.runLater(() -> {
            if (editor.getText().equals(text.get())) {
                return;
            }
            int caret = Math.min(editor.getCaretPosition(), text.get().length());
            synchronizing = true;
            editor.replaceText(text.get());
            editor.moveTo(caret);
            synchronizing = false;
            applyHighlighting();
        });
    }
}
