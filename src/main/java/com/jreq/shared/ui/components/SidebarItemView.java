package com.jreq.shared.ui.components;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public final class SidebarItemView extends HBox {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    public SidebarItemView(String marker, String text) {
        getStyleClass().add("sidebar-item");
        setAlignment(Pos.CENTER_LEFT);

        Label markerLabel = new Label(marker);
        markerLabel.getStyleClass().add("sidebar-item-marker");
        Label textLabel = new Label(text);
        textLabel.getStyleClass().add("sidebar-item-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label action = new Label("›");
        action.getStyleClass().add("muted-label");
        getChildren().addAll(markerLabel, textLabel, spacer, action);
        setAccessibleText(text);
    }

    public void setSelected(boolean selected) {
        pseudoClassStateChanged(SELECTED, selected);
    }
}
