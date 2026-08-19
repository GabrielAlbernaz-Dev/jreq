package com.jreq.shared.ui.components;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public final class ResponseMetadataView extends HBox {
    private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");
    private final StringProperty status = new SimpleStringProperty("—");
    private final StringProperty duration = new SimpleStringProperty("—");
    private final StringProperty size = new SimpleStringProperty("—");
    private final Label statusName;
    private final Label durationName;
    private final Label sizeName;
    private final Label firstDivider;
    private final Label secondDivider;

    public ResponseMetadataView() {
        getStyleClass().add("response-metadata");
        setAlignment(Pos.CENTER_LEFT);
        statusName = metricName("Status");
        durationName = metricName("Time");
        sizeName = metricName("Size");
        firstDivider = divider();
        secondDivider = divider();
        getChildren().addAll(
                metric(statusName, status, "status-success"),
                firstDivider,
                metric(durationName, duration, ""),
                secondDivider,
                metric(sizeName, size, "")
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

    public void setCompact(boolean compact) {
        pseudoClassStateChanged(COMPACT, compact);
        boolean showNames = !compact;
        statusName.setVisible(showNames);
        statusName.setManaged(showNames);
        durationName.setVisible(showNames);
        durationName.setManaged(showNames);
        sizeName.setVisible(showNames);
        sizeName.setManaged(showNames);
        firstDivider.setVisible(showNames);
        firstDivider.setManaged(showNames);
        secondDivider.setVisible(showNames);
        secondDivider.setManaged(showNames);
    }

    private Label metricName(String name) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("metadata-name");
        return nameLabel;
    }

    private HBox metric(Label nameLabel, StringProperty value, String valueClass) {
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
