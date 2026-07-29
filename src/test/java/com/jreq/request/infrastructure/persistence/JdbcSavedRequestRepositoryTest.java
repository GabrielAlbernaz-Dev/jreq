package com.jreq.request.infrastructure.persistence;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.RequestLocation;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.exception.JReqException;
import com.jreq.shared.json.JReqObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcSavedRequestRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAndReadsARequestDefinition() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("repository.db"));
        new DatabaseInitializer(factory).initialize();
        JdbcSavedRequestRepository repository =
                new JdbcSavedRequestRepository(factory, JReqObjectMapper.create());
        HttpRequestDefinition request = new HttpRequestDefinition(
                UUID.randomUUID(),
                "Search repositories",
                HttpMethod.GET,
                "https://api.example.com/repositories",
                List.of(new KeyValueEntry(UUID.randomUUID(), "q", "javafx", true)),
                List.of(),
                RequestBody.none()
        );

        repository.save(request, RequestLocation.root());

        assertThat(repository.findById(request.id())).get()
                .extracting(saved -> saved.definition())
                .isEqualTo(request);
        assertThat(repository.findById(request.id()).orElseThrow().location())
                .isEqualTo(RequestLocation.root());
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void enforcesCaseInsensitiveRequestNamesWithinEachLocation() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("unique.db"));
        new DatabaseInitializer(factory).initialize();
        JdbcSavedRequestRepository repository =
                new JdbcSavedRequestRepository(factory, JReqObjectMapper.create());
        HttpRequestDefinition first = request("Health check");
        HttpRequestDefinition duplicate = request("health CHECK");

        repository.save(first, RequestLocation.root());

        assertThatThrownBy(() -> repository.save(duplicate, RequestLocation.root()))
                .isInstanceOf(com.jreq.shared.exception.JReqException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void keepsNonUniqueConstraintsAsDatabaseFailures() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("foreign-key.db"));
        new DatabaseInitializer(factory).initialize();
        JdbcSavedRequestRepository repository =
                new JdbcSavedRequestRepository(factory, JReqObjectMapper.create());

        assertThatThrownBy(() -> repository.save(
                request("Orphan request"), RequestLocation.collection(UUID.randomUUID())))
                .isInstanceOfSatisfying(JReqException.class, exception -> {
                    assertThat(exception.category()).isEqualTo(ErrorCategory.DATABASE_ERROR);
                    assertThat(exception).hasMessage("Unable to save the request");
                });
    }

    private HttpRequestDefinition request(String name) {
        return new HttpRequestDefinition(
                UUID.randomUUID(), name, HttpMethod.GET, "https://example.com/health",
                List.of(), List.of(), RequestBody.none());
    }
}
