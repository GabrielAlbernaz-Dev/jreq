package com.jreq.request.infrastructure.persistence;

import com.jreq.bootstrap.DatabaseInitializer;
import com.jreq.request.domain.CookieExpiration;
import com.jreq.request.domain.StoredCookie;
import com.jreq.shared.database.JdbcTransactionManager;
import com.jreq.shared.database.SqliteConnectionFactory;
import com.jreq.shared.exception.JReqException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcCookieRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-08-03T20:00:00Z");

    @TempDir
    Path temporaryDirectory;

    private JdbcCookieRepository repository;

    @BeforeEach
    void setUp() {
        SqliteConnectionFactory factory = new SqliteConnectionFactory(temporaryDirectory.resolve("cookies.db"));
        new DatabaseInitializer(factory).initialize();
        repository = new JdbcCookieRepository(factory, new JdbcTransactionManager(factory));
    }

    @Test
    void roundTripsPersistentCookiesAndNeverPersistsSessionCookies() {
        StoredCookie persistent = cookie(
                "remember", "unicode-ç-value", CookieExpiration.at(NOW.plusSeconds(3600)));
        StoredCookie session = cookie("session", "memory-only", CookieExpiration.session());

        repository.replaceAll(List.of(persistent, session));

        assertThat(repository.findAll(NOW)).containsExactly(persistent);
    }

    @Test
    void replacesTheSnapshotAndFiltersExpiredRowsDuringHydration() {
        StoredCookie removed = cookie("removed", "old", CookieExpiration.at(NOW.plusSeconds(60)));
        StoredCookie expired = cookie("expired", "old", CookieExpiration.at(NOW.minusSeconds(1)));
        repository.replaceAll(List.of(removed, expired));
        StoredCookie replacement = cookie("replacement", "new", CookieExpiration.at(NOW.plusSeconds(120)));

        repository.replaceAll(List.of(replacement));

        assertThat(repository.findAll(NOW)).containsExactly(replacement);
    }

    @Test
    void rollsBackAConflictingReplacementWithoutLosingThePreviousJar() {
        StoredCookie original = cookie("original", "safe", CookieExpiration.at(NOW.plusSeconds(60)));
        repository.replaceAll(List.of(original));
        StoredCookie duplicateOne = cookie("duplicate", "one", CookieExpiration.at(NOW.plusSeconds(60)));
        StoredCookie duplicateTwo = new StoredCookie(
                UUID.randomUUID(), duplicateOne.name(), "two", duplicateOne.domain(), duplicateOne.path(),
                duplicateOne.hostOnly(), duplicateOne.secure(), duplicateOne.httpOnly(),
                duplicateOne.expiration(), duplicateOne.createdAt(), duplicateOne.updatedAt());

        assertThatThrownBy(() -> repository.replaceAll(List.of(duplicateOne, duplicateTwo)))
                .isInstanceOf(JReqException.class);
        assertThat(repository.findAll(NOW)).containsExactly(original);
    }

    private StoredCookie cookie(String name, String value, CookieExpiration expiration) {
        return new StoredCookie(
                UUID.randomUUID(), name, value, "api.example.com", "/", true,
                true, true, expiration, NOW, NOW.plusSeconds(1));
    }
}
