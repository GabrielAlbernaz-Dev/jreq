package com.jreq.shared.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.Arrays;
import java.util.Objects;

public final class ResponsiveLayoutManager {
    public static final double COMPACT_MAX_WIDTH = 900;
    public static final double WIDE_MIN_WIDTH = 1_300;

    private final Parent root;
    private final BooleanProperty sidebarExpanded;
    private final ObjectProperty<ResponsiveLayoutMode> mode =
            new SimpleObjectProperty<>(ResponsiveLayoutMode.NORMAL);
    private boolean restoreSidebarAfterCompact;

    public ResponsiveLayoutManager(Parent root, BooleanProperty sidebarExpanded) {
        this.root = Objects.requireNonNull(root, "root");
        this.sidebarExpanded = Objects.requireNonNull(sidebarExpanded, "sidebarExpanded");
    }

    public void attach(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        scene.widthProperty().addListener((observable, oldWidth, newWidth) -> applyWidth(newWidth.doubleValue()));
        applyWidth(scene.getWidth());
    }

    public void applyWidth(double width) {
        ResponsiveLayoutMode nextMode = modeForWidth(width);
        if (nextMode == mode.get() && root.getStyleClass().contains(nextMode.styleClass())) {
            return;
        }

        ResponsiveLayoutMode previousMode = mode.get();
        root.getStyleClass().removeAll(Arrays.stream(ResponsiveLayoutMode.values())
                .map(ResponsiveLayoutMode::styleClass)
                .toList());
        root.getStyleClass().add(nextMode.styleClass());
        mode.set(nextMode);

        if (nextMode == ResponsiveLayoutMode.COMPACT && previousMode != ResponsiveLayoutMode.COMPACT) {
            restoreSidebarAfterCompact = sidebarExpanded.get();
            sidebarExpanded.set(false);
        } else if (previousMode == ResponsiveLayoutMode.COMPACT
                && nextMode != ResponsiveLayoutMode.COMPACT
                && restoreSidebarAfterCompact) {
            sidebarExpanded.set(true);
            restoreSidebarAfterCompact = false;
        }
    }

    public ReadOnlyObjectProperty<ResponsiveLayoutMode> modeProperty() {
        return mode;
    }

    public static ResponsiveLayoutMode modeForWidth(double width) {
        if (width < COMPACT_MAX_WIDTH) {
            return ResponsiveLayoutMode.COMPACT;
        }
        if (width > WIDE_MIN_WIDTH) {
            return ResponsiveLayoutMode.WIDE;
        }
        return ResponsiveLayoutMode.NORMAL;
    }
}
