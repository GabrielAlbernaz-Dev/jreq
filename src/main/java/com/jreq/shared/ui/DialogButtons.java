package com.jreq.shared.ui;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

public final class DialogButtons {
    private DialogButtons() {
    }

    public static ButtonType cancel() {
        return new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    }

    public static ButtonType ok() {
        return new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    }
}
