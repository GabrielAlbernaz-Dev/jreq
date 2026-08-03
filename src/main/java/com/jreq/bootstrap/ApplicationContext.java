package com.jreq.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.CollectionRepository;
import com.jreq.request.application.BasicAuthenticationStrategy;
import com.jreq.request.application.EnvironmentRepository;
import com.jreq.request.application.HttpExecutor;
import com.jreq.request.application.RequestHistoryRepository;
import com.jreq.request.application.JwtBearerAuthenticationStrategy;
import com.jreq.request.application.RequestAuthenticationApplicator;
import com.jreq.request.application.SavedRequestRepository;
import com.jreq.request.application.WorkspaceService;
import com.jreq.request.application.RequestVariableResolver;
import com.jreq.request.infrastructure.http.JavaHttpExecutor;
import com.jreq.request.infrastructure.persistence.JdbcCollectionRepository;
import com.jreq.request.infrastructure.persistence.JdbcEnvironmentRepository;
import com.jreq.request.infrastructure.persistence.JdbcRequestHistoryRepository;
import com.jreq.request.infrastructure.persistence.JdbcSavedRequestRepository;
import com.jreq.request.presentation.MainController;
import com.jreq.request.presentation.MainViewModel;
import com.jreq.request.presentation.ResponseBodyFormatter;
import com.jreq.shared.database.JdbcTransactionManager;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.concurrent.ExecutorServiceTaskExecutor;
import com.jreq.shared.json.JReqObjectMapper;

import java.util.List;

public final class ApplicationContext implements AutoCloseable {
    private static final String DATABASE_THREAD_NAME = "jreq-database";
    private static final String RESPONSE_FORMATTING_THREAD_NAME = "jreq-response-formatting";

    private final ApplicationConfiguration configuration;
    private final MainViewModel mainViewModel;
    private final ExecutorServiceTaskExecutor databaseExecutor;
    private final ExecutorServiceTaskExecutor responseFormattingExecutor;

    private ApplicationContext(
            ApplicationConfiguration configuration,
            MainViewModel mainViewModel,
            ExecutorServiceTaskExecutor databaseExecutor,
            ExecutorServiceTaskExecutor responseFormattingExecutor
    ) {
        this.configuration = configuration;
        this.mainViewModel = mainViewModel;
        this.databaseExecutor = databaseExecutor;
        this.responseFormattingExecutor = responseFormattingExecutor;
    }

    public static ApplicationContext create() {
        ApplicationConfiguration configuration = ApplicationConfiguration.load();
        PersistenceComponents persistence = initializePersistence(configuration);
        ExecutorServiceTaskExecutor databaseExecutor =
                ExecutorServiceTaskExecutor.singleThread(DATABASE_THREAD_NAME);
        ExecutorServiceTaskExecutor responseFormattingExecutor =
                ExecutorServiceTaskExecutor.singleThread(RESPONSE_FORMATTING_THREAD_NAME);
        try {
            return composeApplication(
                    configuration, persistence, databaseExecutor, responseFormattingExecutor);
        } catch (RuntimeException | Error failure) {
            responseFormattingExecutor.close();
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
        EnvironmentRepository environmentRepository =
                new JdbcEnvironmentRepository(connectionFactory, transactionManager);

        return new PersistenceComponents(
                collectionRepository,
                savedRequestRepository,
                historyRepository,
                environmentRepository,
                objectMapper);
    }

    private static ApplicationContext composeApplication(
            ApplicationConfiguration configuration,
            PersistenceComponents persistence,
            ExecutorServiceTaskExecutor databaseExecutor,
            ExecutorServiceTaskExecutor responseFormattingExecutor
    ) {
        HttpExecutor httpExecutor = new JavaHttpExecutor(configuration.httpTimeout());
        RequestVariableResolver variableResolver = new RequestVariableResolver();
        RequestAuthenticationApplicator authenticationApplicator = new RequestAuthenticationApplicator(List.of(
                new BasicAuthenticationStrategy(),
                new JwtBearerAuthenticationStrategy()));
        WorkspaceService workspaceService = new WorkspaceService(
                persistence.collections(),
                persistence.savedRequests(),
                persistence.history(),
                persistence.environments(),
                httpExecutor,
                databaseExecutor,
                variableResolver,
                authenticationApplicator);
        return new ApplicationContext(
                configuration,
                new MainViewModel(
                        workspaceService,
                        variableResolver,
                        new ResponseBodyFormatter(persistence.objectMapper()),
                        responseFormattingExecutor),
                databaseExecutor,
                responseFormattingExecutor);
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
        responseFormattingExecutor.close();
        databaseExecutor.close();
    }

    private record PersistenceComponents(
            CollectionRepository collections,
            SavedRequestRepository savedRequests,
            RequestHistoryRepository history,
            EnvironmentRepository environments,
            ObjectMapper objectMapper
    ) {
    }
}
