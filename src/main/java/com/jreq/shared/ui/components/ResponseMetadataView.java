package com.jreq.shared.ui.components;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public final class ResponseMetadataView extends HBox {
    private final StringProperty status = new SimpleStringProperty("—");
    private final StringProperty duration = new SimpleStringProperty("—");
    private final StringProperty size = new SimpleStringProperty("—");

    public ResponseMetadataView() {
        getStyleClass().add("response-metadata");
        setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(
                metric("Status", status, "status-success"),
                divider(),
                metric("Time", duration, ""),
                divider(),
                metric("Size", size, "")
        );
    }

    public StringProperty statusProperty() {
        return status;
    }

    public StringProperty durationProperty() {
        return duration;
    }

    public StringProperty sizeProperty() {
        return size;
    }

    private HBox metric(String name, StringProperty value, String valueClass) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("metadata-name");
        Label valueLabel = new Label();
        valueLabel.textProperty().bind(value);
        valueLabel.getStyleClass().add("metadata-value");
        if (!valueClass.isEmpty()) {
            valueLabel.getStyleClass().add(valueClass);
        }
        return new HBox(nameLabel, valueLabel);
    }

    private Label divider() {
        Label divider = new Label("|");
        divider.getStyleClass().add("metadata-divider");
        return divider;
    }
}
