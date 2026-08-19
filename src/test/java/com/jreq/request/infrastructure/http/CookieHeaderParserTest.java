package com.jreq.request.infrastructure.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CookieHeaderParserTest {
    private static final URI HTTPS_ORIGIN = URI.create("https://api.example.com/users/profile");
    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private final CookieHeaderParser parser = new CookieHeaderParser();

    @Test
    void parsesFutureExpiresWithoutMaxAge() {
        CookieHeaderParser.ParseReport report = parser.parsePaste(
                "session=abc; Path=/; Expires=Wed, 18 Aug 2027 12:00:00 GMT",
                HTTPS_ORIGIN,
                NOW);

        assertThat(report.accepted()).singleElement().satisfies(cookie -> {
            assertThat(cookie.name()).isEqualTo("session");
            assertThat(cookie.value()).isEqualTo("abc");
            assertThat(cookie.isDeletion()).isFalse();
            assertThat(cookie.expiration()).isInstanceOf(CookieHeaderParser.Expiration.At.class);
        });
        assertThat(report.rejected()).isEmpty();
    }

    @Test
    void treatsMaxAgeZeroAndPastExpiresAsDeletion() {
        assertThat(parser.parseSetCookie("session=x; Max-Age=0; Path=/", HTTPS_ORIGIN, NOW))
                .get()
                .extracting(CookieHeaderParser.ParsedCookie::isDeletion)
                .isEqualTo(true);
        assertThat(parser.parseSetCookie(
                "session=x; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT", HTTPS_ORIGIN, NOW))
                .get()
                .extracting(CookieHeaderParser.ParsedCookie::isDeletion)
                .isEqualTo(true);
    }

    @Test
    void prefersMaxAgeOverExpires() {
        CookieHeaderParser.ParsedCookie cookie = parser.parseSetCookie(
                "a=1; Max-Age=120; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/",
                HTTPS_ORIGIN,
                NOW).orElseThrow();

        assertThat(cookie.isDeletion()).isFalse();
        assertThat(cookie.expiration()).isInstanceOf(CookieHeaderParser.Expiration.At.class);
    }

    @Test
    void parsesCookieRequestHeaderPairsAsHostOnlySessionCookies() {
        CookieHeaderParser.ParseReport report = parser.parsePaste(
                "session=abc; theme=dark",
                HTTPS_ORIGIN,
                NOW);

        assertThat(report.accepted()).extracting(CookieHeaderParser.ParsedCookie::name)
                .containsExactly("session", "theme");
        assertThat(report.accepted()).allSatisfy(cookie -> {
            assertThat(cookie.hostOnly()).isTrue();
            assertThat(cookie.domain()).isEqualTo("api.example.com");
            assertThat(cookie.path()).isEqualTo("/users");
            assertThat(cookie.expiration()).isInstanceOf(CookieHeaderParser.Expiration.Session.class);
        });
    }

    @Test
    void rejectsUnsafeValuesAndOversizedPasteWithoutEchoingSecrets() {
        CookieHeaderParser.ParseReport control = parser.parsePaste(
                "session=ab\rc; Path=/",
                HTTPS_ORIGIN,
                NOW);
        assertThat(control.accepted()).isEmpty();
        assertThat(control.rejected()).isNotEmpty();
        assertThat(control.rejected().getFirst().reason()).doesNotContain("ab");

        String huge = "a=" + "x".repeat(CookieHeaderParser.MAX_PASTE_CHARS);
        CookieHeaderParser.ParseReport oversized = parser.parsePaste(huge, HTTPS_ORIGIN, NOW);
        assertThat(oversized.accepted()).isEmpty();
        assertThat(oversized.rejected().getFirst().reason()).contains("maximum allowed size");
    }

    @Test
    void enforcesSecurePrefixRulesOnPaste() {
        CookieHeaderParser.ParseReport report = parser.parsePaste(
                "__Host-id=1; Path=/; Secure",
                URI.create("http://api.example.com/"),
                NOW);

        assertThat(report.accepted()).isEmpty();
        assertThat(report.rejected().getFirst().reason()).contains("__Host-");
    }

    @Test
    void stripsSetCookiePrefixAndUnknownAttributes() {
        CookieHeaderParser.ParsedCookie cookie = parser.parseSetCookie(
                "Set-Cookie: id=1; Domain=example.com; Path=/; Secure; HttpOnly; SameSite=Lax",
                HTTPS_ORIGIN,
                NOW).orElseThrow();

        assertThat(cookie.name()).isEqualTo("id");
        assertThat(cookie.domain()).isEqualTo("example.com");
        assertThat(cookie.hostOnly()).isFalse();
        assertThat(cookie.secure()).isTrue();
        assertThat(cookie.httpOnly()).isTrue();
    }
}
