package com.jreq.shared.ui;

import javafx.scene.control.Alert;
import javafx.stage.Window;

public final class ErrorAlert {
    private ErrorAlert() {
    }

    public static void show(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle("jREQ — " + title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.getDialogPane().getButtonTypes().setAll(DialogButtons.ok());
        alert.getDialogPane().getStylesheets().addAll(
                resource("/css/theme.css"),
                resource("/css/components.css")
        );
        alert.showAndWait();
    }

    private static String resource(String path) {
        return ErrorAlert.class.getResource(path).toExternalForm();
    }
}
