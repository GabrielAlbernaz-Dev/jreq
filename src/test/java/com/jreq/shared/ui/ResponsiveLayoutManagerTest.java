package com.jreq.shared.ui;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsiveLayoutManagerTest {
    @Test
    void classifiesLayoutWidthsAtDefinedThresholds() {
        assertThat(ResponsiveLayoutManager.modeForWidth(760)).isEqualTo(ResponsiveLayoutMode.COMPACT);
        assertThat(ResponsiveLayoutManager.modeForWidth(899.9)).isEqualTo(ResponsiveLayoutMode.COMPACT);
        assertThat(ResponsiveLayoutManager.modeForWidth(900)).isEqualTo(ResponsiveLayoutMode.NORMAL);
        assertThat(ResponsiveLayoutManager.modeForWidth(1_300)).isEqualTo(ResponsiveLayoutMode.NORMAL);
        assertThat(ResponsiveLayoutManager.modeForWidth(1_301)).isEqualTo(ResponsiveLayoutMode.WIDE);
    }

    @Test
    void appliesExpectedCssClassAtAcceptanceWidthsAndRestoresSidebar() {
        Pane root = new Pane();
        SimpleBooleanProperty sidebarExpanded = new SimpleBooleanProperty(true);
        ResponsiveLayoutManager manager = new ResponsiveLayoutManager(root, sidebarExpanded);

        manager.applyWidth(760);
        assertThat(root.getStyleClass()).contains("layout-compact");
        assertThat(sidebarExpanded.get()).isFalse();

        manager.applyWidth(1_280);
        assertThat(root.getStyleClass())
                .contains("layout-normal")
                .doesNotContain("layout-compact", "layout-wide");
        assertThat(sidebarExpanded.get()).isTrue();

        manager.applyWidth(1_920);
        assertThat(root.getStyleClass())
                .contains("layout-wide")
                .doesNotContain("layout-compact", "layout-normal");
    }
}
