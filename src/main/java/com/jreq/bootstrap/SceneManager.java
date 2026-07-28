package com.jreq.bootstrap;

import com.jreq.request.presentation.MainController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

public final class SceneManager {
    private static final double INITIAL_WIDTH = 1_280;
    private static final double INITIAL_HEIGHT = 800;
    private static final double MIN_WIDTH = 760;
    private static final double MIN_HEIGHT = 560;

    private final ApplicationContext applicationContext;

    public SceneManager(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
    }

    public void showMainWindow(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(resource("/fxml/main-view.fxml"));
        loader.setControllerFactory(applicationContext::createController);
        Parent root = loader.load();
        Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().addAll(List.of(
                resource("/css/theme.css").toExternalForm(),
                resource("/css/components.css").toExternalForm(),
                resource("/css/responsive.css").toExternalForm()
        ));

        MainController controller = loader.getController();
        controller.installSceneBehavior(scene);

        stage.setTitle("jREQ");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.show();
    }

    private URL resource(String path) {
        return Objects.requireNonNull(SceneManager.class.getResource(path), "Missing resource: " + path);
    }
}
