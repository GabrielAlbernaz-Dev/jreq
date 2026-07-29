package com.jreq.request.infrastructure.persistence;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.RequestBody;
import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestLocation;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.exception.JReqException;
import com.jreq.shared.json.JReqObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcCollectionRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private JdbcCollectionRepository collections;
    private JdbcSavedRequestRepository requests;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("workspace.db"));
        new DatabaseInitializer(factory).initialize();
        collections = new JdbcCollectionRepository(factory, JReqObjectMapper.create());
        requests = new JdbcSavedRequestRepository(factory, JReqObjectMapper.create());
    }

    @Test
    void createsRenamesAndEnforcesCaseInsensitiveNames() {
        RequestCollection collection = collection("Users");

        RequestCollection saved = collections.save(collection);
        RequestCollection renamed = collections.save(saved.withName("Accounts"));

        assertThat(collections.findAll()).containsExactly(renamed);
        assertThatThrownBy(() -> collections.save(collection("accounts")))
                .isInstanceOf(JReqException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void movesRequestsToRootWhenDeletingACollectionByDefault() {
        RequestCollection collection = collections.save(collection("Users"));
        HttpRequestDefinition request = request("List users");
        requests.save(request, RequestLocation.collection(collection.id()));

        collections.deleteById(collection.id(), false);

        assertThat(collections.findAll()).isEmpty();
        assertThat(requests.findById(request.id()).orElseThrow().location())
                .isEqualTo(RequestLocation.root());
    }

    @Test
    void renamesARequestWhenMovingItWouldCollideAtTheRoot() {
        RequestCollection collection = collections.save(collection("Users"));
        HttpRequestDefinition rootRequest = request("List users");
        HttpRequestDefinition containedRequest = request("List users");
        requests.save(rootRequest, RequestLocation.root());
        requests.save(containedRequest, RequestLocation.collection(collection.id()));

        collections.deleteById(collection.id(), false);

        assertThat(requests.findById(containedRequest.id()).orElseThrow().definition().name())
                .isEqualTo("List users (2)");
        assertThat(requests.findById(containedRequest.id()).orElseThrow().location())
                .isEqualTo(RequestLocation.root());
    }

    @Test
    void deletesContainedRequestsWhenExplicitlyRequested() {
        RequestCollection collection = collections.save(collection("Users"));
        HttpRequestDefinition request = request("List users");
        requests.save(request, RequestLocation.collection(collection.id()));

        collections.deleteById(collection.id(), true);

        assertThat(requests.findById(request.id())).isEmpty();
    }

    private RequestCollection collection(String name) {
        Instant now = Instant.now();
        return new RequestCollection(UUID.randomUUID(), name, now, now);
    }

    private HttpRequestDefinition request(String name) {
        return new HttpRequestDefinition(
                UUID.randomUUID(), name, HttpMethod.GET, "https://example.com/users",
                List.of(), List.of(), RequestBody.none());
    }
}
