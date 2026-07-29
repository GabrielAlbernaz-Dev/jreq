package com.jreq.request.infrastructure.persistence;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.application.HttpResponseFailure;
import com.jreq.request.application.HttpResponseSuccess;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.RequestHistoryEntry;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.json.JReqObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRequestHistoryRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private JdbcRequestHistoryRepository repository;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("history.db"));
        new DatabaseInitializer(factory).initialize();
        repository = new JdbcRequestHistoryRepository(factory, JReqObjectMapper.create());
    }

    @Test
    void persistsCompleteSuccessAndFailureSnapshots() {
        byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        RequestHistoryEntry success = entry(1, new HttpResponseSuccess(
                201, Map.of("content-type", List.of("application/json")), body,
                Duration.ofMillis(42), body.length));
        RequestHistoryEntry failure = entry(2, new HttpResponseFailure(
                ErrorCategory.TIMEOUT, "The request timed out.", Duration.ofSeconds(30)));

        repository.appendAndTrim(success, 100);
        repository.appendAndTrim(failure, 100);

        assertThat(repository.findRecent(100))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(failure, success);
    }

    @Test
    void retainsOnlyTheConfiguredNumberOfNewestEntries() {
        for (int index = 0; index < 105; index++) {
            repository.appendAndTrim(entry(index, new HttpResponseFailure(
                    ErrorCategory.TIMEOUT, "Timeout " + index, Duration.ofMillis(index))), 100);
        }

        List<RequestHistoryEntry> recent = repository.findRecent(100);

        assertThat(recent).hasSize(100);
        assertThat(recent.getFirst().name()).isEqualTo("Request 104");
        assertThat(recent.getLast().name()).isEqualTo("Request 5");
    }

    @Test
    void deletesOneEntryAndThenClearsHistory() {
        RequestHistoryEntry first = entry(1, new HttpResponseFailure(
                ErrorCategory.UNKNOWN, "Failure", Duration.ZERO));
        RequestHistoryEntry second = entry(2, new HttpResponseFailure(
                ErrorCategory.UNKNOWN, "Failure", Duration.ZERO));
        repository.appendAndTrim(first, 100);
        repository.appendAndTrim(second, 100);

        repository.deleteById(second.id());
        assertThat(repository.findRecent(100)).extracting(RequestHistoryEntry::id)
                .containsExactly(first.id());

        repository.deleteAll();
        assertThat(repository.findRecent(100)).isEmpty();
    }

    private RequestHistoryEntry entry(int sequence, com.jreq.request.application.HttpResponseResult result) {
        HttpRequestDefinition request = new HttpRequestDefinition(
                UUID.nameUUIDFromBytes(("request-" + sequence).getBytes(StandardCharsets.UTF_8)),
                "Request " + sequence,
                HttpMethod.GET,
                "https://example.com/" + sequence,
                List.of(), List.of(), RequestBody.none());
        return new RequestHistoryEntry(
                UUID.nameUUIDFromBytes(("history-" + sequence).getBytes(StandardCharsets.UTF_8)),
                request.name(), request, result, Instant.EPOCH.plusSeconds(sequence));
    }
}
