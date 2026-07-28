package com.jreq.shared.ui;

public enum ResponsiveLayoutMode {
    COMPACT("layout-compact"),
    NORMAL("layout-normal"),
    WIDE("layout-wide");

    private final String styleClass;

    ResponsiveLayoutMode(String styleClass) {
        this.styleClass = styleClass;
    }

    public String styleClass() {
        return styleClass;
    }
}
