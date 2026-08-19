package com.jreq.shared.ui.components;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public final class SidebarItemView extends HBox {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    public SidebarItemView(String marker, String text) {
        getStyleClass().add("sidebar-item");
        setAlignment(Pos.CENTER_LEFT);

        Label markerLabel = new Label(marker);
        markerLabel.getStyleClass().add("sidebar-item-marker");
        Label textLabel = new Label(text);
        textLabel.getStyleClass().add("sidebar-item-text");
        textLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        textLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textLabel, Priority.ALWAYS);
        Label action = new Label("›");
        action.getStyleClass().add("muted-label");
        getChildren().addAll(markerLabel, textLabel, action);
        setAccessibleText(text);
    }

    public void setSelected(boolean selected) {
        pseudoClassStateChanged(SELECTED, selected);
    }
}
