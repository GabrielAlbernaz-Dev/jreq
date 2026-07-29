package com.jreq.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.CollectionRepository;
import com.jreq.request.application.HttpExecutor;
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

public final class ApplicationContext implements AutoCloseable {
    private static final String DATABASE_THREAD_NAME = "jreq-database";

    private final ApplicationConfiguration configuration;
    private final MainViewModel mainViewModel;
    private final ExecutorServiceTaskExecutor databaseExecutor;

    private ApplicationContext(
            ApplicationConfiguration configuration,
            MainViewModel mainViewModel,
            ExecutorServiceTaskExecutor databaseExecutor
    ) {
        this.configuration = configuration;
        this.mainViewModel = mainViewModel;
        this.databaseExecutor = databaseExecutor;
    }

    public static ApplicationContext create() {
        ApplicationConfiguration configuration = ApplicationConfiguration.load();
        PersistenceComponents persistence = initializePersistence(configuration);
        ExecutorServiceTaskExecutor databaseExecutor =
                ExecutorServiceTaskExecutor.singleThread(DATABASE_THREAD_NAME);
        try {
            return composeApplication(configuration, persistence, databaseExecutor);
        } catch (RuntimeException | Error failure) {
            databaseExecutor.close();
            throw failure;
        }
    }

    private static PersistenceComponents initializePersistence(ApplicationConfiguration configuration) {
        AppDirectories directories = AppDirectories.systemDefault();
        directories.ensureDataDirectory();

        ObjectMapper objectMapper = JReqObjectMapper.create();
        SqliteConnectionFactory connectionFactory = new SqliteConnectionFactory(
                directories.databasePath(configuration.databaseFilename()));
        new DatabaseInitializer(connectionFactory).initialize();
        JdbcTransactionManager transactionManager = new JdbcTransactionManager(connectionFactory);

        CollectionRepository collectionRepository =
                new JdbcCollectionRepository(connectionFactory, transactionManager, objectMapper);
        SavedRequestRepository savedRequestRepository =
                new JdbcSavedRequestRepository(connectionFactory, objectMapper);
        RequestHistoryRepository historyRepository =
                new JdbcRequestHistoryRepository(connectionFactory, transactionManager, objectMapper);

        return new PersistenceComponents(
                collectionRepository, savedRequestRepository, historyRepository);
    }

    private static ApplicationContext composeApplication(
            ApplicationConfiguration configuration,
            PersistenceComponents persistence,
            ExecutorServiceTaskExecutor databaseExecutor
    ) {
        HttpExecutor httpExecutor = new JavaHttpExecutor(configuration.httpTimeout());
        WorkspaceService workspaceService = new WorkspaceService(
                persistence.collections(),
                persistence.savedRequests(),
                persistence.history(),
                httpExecutor,
                databaseExecutor);
        return new ApplicationContext(configuration, new MainViewModel(workspaceService), databaseExecutor);
    }

    public Object createController(Class<?> controllerType) {
        if (controllerType == MainController.class) {
            return new MainController(mainViewModel);
        }
        throw new IllegalArgumentException("Unsupported FXML controller: " + controllerType.getName());
    }

    String windowTitle() {
        return configuration.windowTitle();
    }

    @Override
    public void close() {
        databaseExecutor.close();
    }

    private record PersistenceComponents(
            CollectionRepository collections,
            SavedRequestRepository savedRequests,
            RequestHistoryRepository history
    ) {
    }
}
