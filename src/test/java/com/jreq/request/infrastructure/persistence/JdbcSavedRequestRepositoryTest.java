package com.jreq.request.infrastructure.persistence;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestBody;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.json.JReqObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

        repository.save(request);

        assertThat(repository.findById(request.id())).contains(request);
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }
}
