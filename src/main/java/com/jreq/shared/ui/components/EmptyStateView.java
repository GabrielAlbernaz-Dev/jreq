package com.jreq.shared.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class EmptyStateView extends VBox {
    private final Label title = new Label("Nothing here yet");
    private final Label description = new Label("Content will appear here when available.");

    public EmptyStateView() {
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER);
        title.getStyleClass().add("empty-state-title");
        description.getStyleClass().add("muted-label");
        description.setWrapText(true);
        getChildren().addAll(title, description);
    }

    public void setTitle(String value) {
        title.setText(value);
    }

    public void setDescription(String value) {
        description.setText(value);
    }
}
