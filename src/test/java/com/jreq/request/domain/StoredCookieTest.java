package com.jreq.request.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoredCookieTest {
    private static final Instant NOW = Instant.parse("2026-08-03T20:00:00Z");

    @Test
    void normalizesDomainAndPathWithoutChangingSensitiveValues() {
        StoredCookie cookie = new StoredCookie(
                UUID.randomUUID(), "session", "Case-Sensitive-Value", ".API.Example.COM", "/users",
                false, true, true, CookieExpiration.at(NOW.plusSeconds(60)), NOW, NOW);

        assertThat(cookie.domain()).isEqualTo("api.example.com");
        assertThat(cookie.path()).isEqualTo("/users");
        assertThat(cookie.value()).isEqualTo("Case-Sensitive-Value");
    }

    @Test
    void distinguishesSessionAndExpiredPersistentCookies() {
        StoredCookie session = cookie("session", CookieExpiration.session());
        StoredCookie expired = cookie("expired", CookieExpiration.at(NOW.minusSeconds(1)));

        assertThat(session.isSession()).isTrue();
        assertThat(session.isExpiredAt(NOW.plusSeconds(10_000))).isFalse();
        assertThat(expired.isExpiredAt(NOW)).isTrue();
    }

    @Test
    void rejectsHeaderInjectionWithoutEchoingTheSecret() {
        String secret = "private-value\r\nX-Injected: yes";

        assertThatThrownBy(() -> new StoredCookie(
                UUID.randomUUID(), "session", secret, "example.com", "/",
                true, false, false, CookieExpiration.session(), NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("private-value")
                .hasMessageNotContaining("X-Injected");
    }

    @Test
    void rejectsInvalidNamesDomainsAndPaths() {
        assertThatThrownBy(() -> cookie("bad name", CookieExpiration.session()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoredCookie(
                UUID.randomUUID(), "name", "value", "example.com\nother", "/",
                true, false, false, CookieExpiration.session(), NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoredCookie(
                UUID.randomUUID(), "name", "value", "example.com", "relative",
                true, false, false, CookieExpiration.session(), NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesSecurePrefixContractsForManuallyManagedCookies() {
        assertThatThrownBy(() -> new StoredCookie(
                UUID.randomUUID(), "__Secure-token", "value", "example.com", "/",
                true, false, true, CookieExpiration.session(), NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Secure");
        assertThatThrownBy(() -> new StoredCookie(
                UUID.randomUUID(), "__Host-token", "value", "example.com", "/account",
                true, true, true, CookieExpiration.session(), NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host-only");
    }

    private StoredCookie cookie(String name, CookieExpiration expiration) {
        return new StoredCookie(
                UUID.randomUUID(), name, "value", "example.com", "/",
                true, false, false, expiration, NOW, NOW);
    }
}
