package com.jreq.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.HttpExecutor;
import com.jreq.request.application.CollectionRepository;
import com.jreq.request.application.RequestHistoryRepository;
import com.jreq.request.application.SavedRequestRepository;
import com.jreq.request.application.WorkspaceService;
import com.jreq.request.infrastructure.http.JavaHttpExecutor;
import com.jreq.request.infrastructure.persistence.JdbcCollectionRepository;
import com.jreq.request.infrastructure.persistence.JdbcRequestHistoryRepository;
import com.jreq.request.infrastructure.persistence.JdbcSavedRequestRepository;
import com.jreq.request.presentation.MainController;
import com.jreq.request.presentation.MainViewModel;
import com.jreq.shared.database.JdbcTransactionManager;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.concurrent.ExecutorServiceTaskExecutor;
import com.jreq.shared.json.JReqObjectMapper;

import java.time.Duration;

public final class ApplicationContext implements AutoCloseable {
    private final ObjectMapper objectMapper;
    private final SavedRequestRepository savedRequestRepository;
    private final HttpExecutor httpExecutor;
    private final MainViewModel mainViewModel;
    private final ExecutorServiceTaskExecutor databaseExecutor;

    private ApplicationContext(
            ObjectMapper objectMapper,
            SavedRequestRepository savedRequestRepository,
            HttpExecutor httpExecutor,
            MainViewModel mainViewModel,
            ExecutorServiceTaskExecutor databaseExecutor
    ) {
        this.objectMapper = objectMapper;
        this.savedRequestRepository = savedRequestRepository;
        this.httpExecutor = httpExecutor;
        this.mainViewModel = mainViewModel;
        this.databaseExecutor = databaseExecutor;
    }

    public static ApplicationContext create() {
        AppDirectories directories = AppDirectories.systemDefault();
        directories.ensureDataDirectory();

        ObjectMapper objectMapper = JReqObjectMapper.create();
        SqliteConnectionFactory connectionFactory = new SqliteConnectionFactory(directories.databasePath());
        JdbcTransactionManager transactionManager = new JdbcTransactionManager(connectionFactory);
        new DatabaseInitializer(connectionFactory).initialize();

        SavedRequestRepository repository = new JdbcSavedRequestRepository(connectionFactory, objectMapper);
        CollectionRepository collectionRepository =
                new JdbcCollectionRepository(connectionFactory, transactionManager, objectMapper);
        RequestHistoryRepository historyRepository =
                new JdbcRequestHistoryRepository(connectionFactory, transactionManager, objectMapper);
        HttpExecutor executor = new JavaHttpExecutor(Duration.ofSeconds(30));
        ExecutorServiceTaskExecutor databaseExecutor =
                ExecutorServiceTaskExecutor.singleThread("jreq-database");
        WorkspaceService workspaceService = new WorkspaceService(
                collectionRepository, repository, historyRepository, executor, databaseExecutor);
        return new ApplicationContext(
                objectMapper, repository, executor, new MainViewModel(workspaceService), databaseExecutor);
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

    @Override
    public void close() {
        databaseExecutor.close();
    }
}
