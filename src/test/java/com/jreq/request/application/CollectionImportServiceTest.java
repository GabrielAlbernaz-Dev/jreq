package com.jreq.request.application;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.domain.RequestEnvironment;
import com.jreq.request.infrastructure.persistence.JdbcCollectionRepository;
import com.jreq.request.infrastructure.persistence.JdbcEnvironmentRepository;
import com.jreq.request.infrastructure.persistence.JdbcSavedRequestRepository;
import com.jreq.shared.concurrent.AsyncTaskExecutor;
import com.jreq.shared.database.JdbcTransactionManager;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.exception.JReqException;
import com.jreq.shared.json.JReqObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionImportServiceTest {
    private static final String VALID_COLLECTION = """
            {"info":{"name":"Imported API",
                      "schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
             "item":[
               {"name":"Ping","request":{"method":"GET","url":"https://example.com/ping"}},
               {"name":"Auth","item":[
                 {"name":"Login","request":{"method":"POST","url":"https://example.com/login",
                   "body":{"mode":"raw","raw":"{}","options":{"raw":{"language":"json"}}}}}
               ]}
             ],
             "variable":[{"key":"host","value":"https://example.com"}]}
            """;

    @TempDir
    Path temporaryDirectory;

    private JdbcCollectionRepository collections;
    private JdbcSavedRequestRepository requests;
    private JdbcEnvironmentRepository environments;
    private CollectionImportService service;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(
                temporaryDirectory.resolve("workspace.db"));
        new DatabaseInitializer(factory).initialize();
        JdbcTransactionManager transactionManager = new JdbcTransactionManager(factory);
        var objectMapper = JReqObjectMapper.create();
        collections = new JdbcCollectionRepository(factory, transactionManager, objectMapper);
        requests = new JdbcSavedRequestRepository(factory, objectMapper);
        environments = new JdbcEnvironmentRepository(factory, transactionManager);
        service = new CollectionImportService(collections, AsyncTaskExecutor.direct());
    }

    @Test
    void importsAValidCollectionEndToEnd() throws IOException {
        Path file = writeJson("collection.json", VALID_COLLECTION);

        CollectionImportResult result = service.importCollection(file).join();

        assertThat(result.collection().name()).isEqualTo("Imported API");
        assertThat(result.importedRequestCount()).isEqualTo(2);
        assertThat(result.skippedRequestCount()).isZero();
        assertThat(collections.findAll())
                .extracting("name")
                .containsExactly("Imported API");
        assertThat(requests.findAll())
                .extracting(request -> request.definition().name())
                .containsExactlyInAnyOrder("Ping", "Auth / Login");
        List<RequestEnvironment> persistedEnvironments = environments.loadConfiguration().environments();
        assertThat(persistedEnvironments).hasSize(1);
        assertThat(persistedEnvironments.getFirst().variables())
                .extracting("key", "value")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("host", "https://example.com"));
    }

    @Test
    void renamesTheImportedCollectionWhenTheNameIsTaken() throws IOException {
        service.importCollection(writeJson("first.json", VALID_COLLECTION)).join();

        CollectionImportResult second =
                service.importCollection(writeJson("second.json", VALID_COLLECTION)).join();

        assertThat(second.collection().name()).isEqualTo("Imported API (2)");
        assertThat(collections.findAll()).hasSize(2);
    }

    @Test
    void rejectsNonJsonExtensionsIncludingExecutables() throws IOException {
        Path textFile = writeJson("notes.txt", VALID_COLLECTION);
        Path executable = writeJson("malware.exe", VALID_COLLECTION);

        assertThatThrownBy(() -> service.importCollection(textFile).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(JReqException.class)
                .rootCause()
                .hasMessageContaining(".json");
        assertThatThrownBy(() -> service.importCollection(executable).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(JReqException.class);
        assertThat(collections.findAll()).isEmpty();
    }

    @Test
    void rejectsMissingFilesAndDirectories() {
        Path missing = temporaryDirectory.resolve("missing.json");
        Path directory = temporaryDirectory.resolve("folder.json");

        assertThatThrownBy(() -> service.importCollection(missing).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(JReqException.class);
        assertThatThrownBy(() -> service.importCollection(directory).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(JReqException.class);
    }

    @Test
    void rejectsEmptyFiles() throws IOException {
        Path empty = writeJson("empty.json", "");

        assertThatThrownBy(() -> service.importCollection(empty).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(JReqException.class)
                .rootCause()
                .hasMessageContaining("does not look like a JSON");
    }

    @Test
    void rejectsFilesAboveTheSizeLimit() throws IOException {
        Path huge = temporaryDirectory.resolve("huge.json");
        try (FileChannel channel = FileChannel.open(
                huge, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.position(CollectionImportService.MAX_FILE_BYTES + 1);
            channel.write(java.nio.ByteBuffer.wrap(new byte[]{1}));
        }

        assertThatThrownBy(() -> service.importCollection(huge).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(JReqException.class)
                .rootCause()
                .hasMessageContaining("10 MB");
        assertThat(collections.findAll()).isEmpty();
    }

    @Test
    void rejectsBinaryContentMasqueradingAsJson() throws IOException {
        Path binary = temporaryDirectory.resolve("binary.json");
        Files.write(binary, new byte[]{0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01, 0x01});

        assertThatThrownBy(() -> service.importCollection(binary).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(JReqException.class)
                .rootCause()
                .hasMessageContaining("does not look like a JSON");
    }

    @Test
    void rejectsMalformedJsonWithoutPersistingAnything() throws IOException {
        Path broken = writeJson("broken.json", "{\"info\":{\"name\":\"X\"");

        assertThatThrownBy(() -> service.importCollection(broken).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(JReqException.class)
                .rootCause()
                .extracting(failure -> ((JReqException) failure).category())
                .isEqualTo(ErrorCategory.VALIDATION_ERROR);
        assertThat(collections.findAll()).isEmpty();
        assertThat(requests.findAll()).isEmpty();
    }

    @Test
    void acceptsJsonWithLeadingWhitespaceOrBom() throws IOException {
        Path whitespace = writeJson("whitespace.json", "  \n" + VALID_COLLECTION);
        Path bom = temporaryDirectory.resolve("bom.json");
        byte[] payload = VALID_COLLECTION.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] withBom = new byte[payload.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(payload, 0, withBom, 3, payload.length);
        Files.write(bom, withBom);

        assertThat(service.importCollection(whitespace).join().importedRequestCount()).isEqualTo(2);
        assertThat(service.importCollection(bom).join().collection().name())
                .isEqualTo("Imported API (2)");
    }

    private Path writeJson(String name, String content) throws IOException {
        return Files.writeString(temporaryDirectory.resolve(name), content);
    }
}
