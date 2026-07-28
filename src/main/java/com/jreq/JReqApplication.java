package com.jreq;

import atlantafx.base.theme.PrimerDark;
import com.jreq.bootstrap.ApplicationContext;
import com.jreq.bootstrap.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JReqApplication extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(JReqApplication.class);

    private ApplicationContext applicationContext;

    @Override
    public void init() {
        applicationContext = ApplicationContext.create();
        LOGGER.info("jREQ application context initialized");
    }

    @Override
    public void start(Stage stage) throws Exception {
        setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        new SceneManager(applicationContext).showMainWindow(stage);
        LOGGER.info("jREQ main window started");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
