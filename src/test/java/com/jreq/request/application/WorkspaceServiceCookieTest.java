package com.jreq.request.application;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.domain.CookieExpiration;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.StoredCookie;
import com.jreq.request.application.CookieJarEdit;
import com.jreq.request.application.ExecutionReport;
import com.jreq.request.infrastructure.http.ManagedCookieStore;
import com.jreq.request.infrastructure.persistence.JdbcCollectionRepository;
import com.jreq.request.infrastructure.persistence.JdbcEnvironmentRepository;
import com.jreq.request.infrastructure.persistence.JdbcRequestHistoryRepository;
import com.jreq.request.infrastructure.persistence.JdbcSavedRequestRepository;
import com.jreq.shared.concurrent.ExecutorServiceTaskExecutor;
import com.jreq.shared.database.JdbcTransactionManager;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.json.JReqObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceServiceCookieTest {
    @TempDir
    Path temporaryDirectory;

    private ExecutorServiceTaskExecutor databaseExecutor;

    @BeforeEach
    void setUp() {
        databaseExecutor = ExecutorServiceTaskExecutor.singleThread("cookie-service-database");
    }

    @AfterEach
    void tearDown() {
        databaseExecutor.close();
    }

    @Test
    void persistsCookieChangesOffCallerThreadAndSkipsUnchangedRevisions() throws Exception {
        ManagedCookieStore jar = new ManagedCookieStore();
        RecordingCookieRepository cookies = new RecordingCookieRepository();
        AtomicBoolean firstExecution = new AtomicBoolean(true);
        HttpExecutor http = request -> {
            if (firstExecution.getAndSet(false)) {
                HttpCookie cookie = new HttpCookie("remember", "sensitive-value");
                cookie.setMaxAge(3600);
                jar.add(URI.create(request.url()), cookie);
            }
            return CompletableFuture.completedFuture(new HttpResponseFailure(
                    ErrorCategory.TIMEOUT, "Timeout", Duration.ZERO));
        };
        WorkspaceService service = service(http, cookies, jar);

        service.executeAndRecord(request()).get(3, TimeUnit.SECONDS);
        service.executeAndRecord(request()).get(3, TimeUnit.SECONDS);

        assertThat(cookies.replaceCount).hasValue(1);
        assertThat(cookies.threadName.get()).isEqualTo("cookie-service-database");
        assertThat(cookies.lastSaved).singleElement()
                .extracting(StoredCookie::name)
                .isEqualTo("remember");
        assertThat(service.loadWorkspace().get(3, TimeUnit.SECONDS).cookies())
                .singleElement().extracting(StoredCookie::value).isEqualTo("sensitive-value");
        assertThat(service.loadWorkspace().get(3, TimeUnit.SECONDS).history().getFirst()
                .request().headers()).isEmpty();
    }

    @Test
    void savesManualCookieEditsOnTheDatabaseExecutor() throws Exception {
        ManagedCookieStore jar = new ManagedCookieStore();
        RecordingCookieRepository cookies = new RecordingCookieRepository();
        WorkspaceService service = service(
                request -> CompletableFuture.completedFuture(new HttpResponseFailure(
                        ErrorCategory.UNKNOWN, "Failure", Duration.ZERO)),
                cookies,
                jar);
        Instant now = Instant.now();
        StoredCookie edited = new StoredCookie(
                UUID.randomUUID(), "manual", "value", "example.com", "/", true,
                true, true, CookieExpiration.at(now.plusSeconds(600)), now, now);

        service.saveCookies(List.of(edited)).get(3, TimeUnit.SECONDS);

        assertThat(cookies.threadName.get()).isEqualTo("cookie-service-database");
        assertThat(jar.snapshot()).containsExactly(edited);
        assertThat(jar.state().dirty()).isFalse();
    }

    @Test
    void mergeSaveKeepsCookiesCapturedWhileDialogWasOpen() throws Exception {
        ManagedCookieStore jar = new ManagedCookieStore();
        Instant now = Instant.now();
        StoredCookie baseline = new StoredCookie(
                UUID.randomUUID(), "baseline", "one", "example.com", "/", true,
                false, false, CookieExpiration.at(now.plusSeconds(600)), now, now);
        jar.restore(List.of(baseline));
        RecordingCookieRepository cookies = new RecordingCookieRepository();
        WorkspaceService service = service(
                request -> CompletableFuture.completedFuture(new HttpResponseFailure(
                        ErrorCategory.UNKNOWN, "Failure", Duration.ZERO)),
                cookies,
                jar);

        StoredCookie captured = new StoredCookie(
                UUID.randomUUID(), "captured", "two", "example.com", "/", true,
                false, false, CookieExpiration.at(now.plusSeconds(600)), now, now);
        jar.replaceAll(List.of(baseline, captured));

        StoredCookie edited = new StoredCookie(
                baseline.id(), "baseline", "updated", "example.com", "/", true,
                false, false, CookieExpiration.at(now.plusSeconds(600)), now, now);
        service.saveCookies(CookieJarEdit.of(List.of(edited), java.util.Set.of(), false))
                .get(3, TimeUnit.SECONDS);

        assertThat(jar.snapshot()).extracting(StoredCookie::name, StoredCookie::value)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("baseline", "updated"),
                        org.assertj.core.groups.Tuple.tuple("captured", "two"));
    }

    @Test
    void persistsFreshCookieStateInsideDatabaseExecutor() throws Exception {
        ManagedCookieStore jar = new ManagedCookieStore();
        RecordingCookieRepository cookies = new RecordingCookieRepository();
        java.util.concurrent.CountDownLatch httpStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch allowComplete = new java.util.concurrent.CountDownLatch(1);
        HttpExecutor http = request -> CompletableFuture.supplyAsync(() -> {
            httpStarted.countDown();
            try {
                assertThat(allowComplete.await(3, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            HttpCookie cookie = new HttpCookie("late", "value");
            cookie.setMaxAge(3600);
            jar.add(URI.create(request.url()), cookie);
            return new HttpResponseFailure(ErrorCategory.TIMEOUT, "Timeout", Duration.ZERO);
        });
        WorkspaceService service = service(http, cookies, jar);

        CompletableFuture<ExecutionReport> execution = service.executeAndRecord(request());
        assertThat(httpStarted.await(3, TimeUnit.SECONDS)).isTrue();
        allowComplete.countDown();
        execution.get(3, TimeUnit.SECONDS);

        assertThat(cookies.lastSaved).singleElement()
                .extracting(StoredCookie::name)
                .isEqualTo("late");
    }

    private WorkspaceService service(
            HttpExecutor http,
            CookieRepository cookies,
            CookieJar jar
    ) {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("service.db"));
        new DatabaseInitializer(factory).initialize();
        var mapper = JReqObjectMapper.create();
        var transactions = new JdbcTransactionManager(factory);
        return new WorkspaceService(
                new JdbcCollectionRepository(factory, transactions, mapper),
                new JdbcSavedRequestRepository(factory, mapper),
                new JdbcRequestHistoryRepository(factory, transactions, mapper),
                new JdbcEnvironmentRepository(factory, transactions),
                cookies,
                jar,
                http,
                databaseExecutor,
                new RequestVariableResolver(),
                new RequestAuthenticationApplicator(List.of(
                        new BasicAuthenticationStrategy(), new JwtBearerAuthenticationStrategy())));
    }

    private HttpRequestDefinition request() {
        return new HttpRequestDefinition(
                UUID.randomUUID(), "Cookies", HttpMethod.GET, "https://example.com/account",
                List.of(), List.of(), RequestBody.none());
    }

    private static final class RecordingCookieRepository implements CookieRepository {
        private final AtomicInteger replaceCount = new AtomicInteger();
        private final AtomicReference<String> threadName = new AtomicReference<>();
        private volatile List<StoredCookie> lastSaved = List.of();

        @Override
        public List<StoredCookie> findAll(Instant now) {
            return lastSaved;
        }

        @Override
        public void replaceAll(List<StoredCookie> cookies) {
            replaceCount.incrementAndGet();
            threadName.set(Thread.currentThread().getName());
            lastSaved = List.copyOf(cookies);
        }
    }
}
