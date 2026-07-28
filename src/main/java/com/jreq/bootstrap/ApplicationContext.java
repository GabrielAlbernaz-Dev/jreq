package com.jreq.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.HttpExecutor;
import com.jreq.request.application.SavedRequestRepository;
import com.jreq.request.infrastructure.http.JavaHttpExecutor;
import com.jreq.request.infrastructure.persistence.JdbcSavedRequestRepository;
import com.jreq.request.presentation.MainController;
import com.jreq.request.presentation.MainViewModel;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.json.JReqObjectMapper;

import java.time.Duration;

public final class ApplicationContext {
    private final ObjectMapper objectMapper;
    private final SavedRequestRepository savedRequestRepository;
    private final HttpExecutor httpExecutor;
    private final MainViewModel mainViewModel;

    private ApplicationContext(
            ObjectMapper objectMapper,
            SavedRequestRepository savedRequestRepository,
            HttpExecutor httpExecutor,
            MainViewModel mainViewModel
    ) {
        this.objectMapper = objectMapper;
        this.savedRequestRepository = savedRequestRepository;
        this.httpExecutor = httpExecutor;
        this.mainViewModel = mainViewModel;
    }

    public static ApplicationContext create() {
        AppDirectories directories = AppDirectories.systemDefault();
        directories.ensureDataDirectory();

        ObjectMapper objectMapper = JReqObjectMapper.create();
        SqliteConnectionFactory connectionFactory = new SqliteConnectionFactory(directories.databasePath());
        new DatabaseInitializer(connectionFactory).initialize();

        SavedRequestRepository repository = new JdbcSavedRequestRepository(connectionFactory, objectMapper);
        HttpExecutor executor = new JavaHttpExecutor(Duration.ofSeconds(30));
        return new ApplicationContext(objectMapper, repository, executor, new MainViewModel());
    }

    public Object createController(Class<?> controllerType) {
        if (controllerType == MainController.class) {
            return new MainController(mainViewModel);
        }
        throw new IllegalArgumentException("Unsupported FXML controller: " + controllerType.getName());
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public SavedRequestRepository savedRequestRepository() {
        return savedRequestRepository;
    }

    public HttpExecutor httpExecutor() {
        return httpExecutor;
    }
}
