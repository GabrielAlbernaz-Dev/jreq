package com.jreq.request.application;

import com.jreq.request.domain.HttpMethod;
import com.jreq.request.domain.HttpRequestDefinition;
import com.jreq.request.domain.KeyValueEntry;
import com.jreq.request.domain.RequestAuthentication;
import com.jreq.request.domain.RequestBody;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestAuthenticationApplicatorTest {
    private final RequestAuthenticationApplicator applicator = new RequestAuthenticationApplicator(List.of(
            new BasicAuthenticationStrategy(),
            new JwtBearerAuthenticationStrategy()));

    @Test
    void appliesBasicAuthenticationAndOverridesManualAuthorization() {
        KeyValueEntry accept = entry("Accept", "application/json", true);
        HttpRequestDefinition request = request(
                List.of(
                        entry("authorization", "Bearer manual", true),
                        entry("AUTHORIZATION", "disabled", false),
                        accept),
                new RequestAuthentication.Basic("Aladdin", "open sesame"));

        HttpRequestDefinition authenticated = applicator.apply(request);

        assertThat(authenticated.headers())
                .extracting(KeyValueEntry::key, KeyValueEntry::value)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Accept", "application/json"),
                        org.assertj.core.groups.Tuple.tuple(
                                "Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="));
        assertThat(request.headers()).hasSize(3);
    }

    @Test
    void appliesATrimmedJwtAsBearerAndLeavesNoneUnchanged() {
        HttpRequestDefinition jwt = request(
                List.of(), new RequestAuthentication.JwtBearer("  header.payload.signature  "));
        HttpRequestDefinition none = request(List.of(entry("Authorization", "Manual", true)),
                RequestAuthentication.none());

        assertThat(applicator.apply(jwt).headers()).singleElement()
                .extracting(KeyValueEntry::value)
                .isEqualTo("Bearer header.payload.signature");
        assertThat(applicator.apply(none)).isSameAs(none);
    }

    @Test
    void validatesConfigurationsWithoutIncludingSensitiveValues() {
        assertThatThrownBy(() -> applicator.apply(request(
                List.of(), new RequestAuthentication.Basic("", "private-password"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a username")
                .hasMessageNotContaining("private-password");
        assertThatThrownBy(() -> applicator.apply(request(
                List.of(), new RequestAuthentication.Basic("invalid:user", "private-password"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot contain ':'")
                .hasMessageNotContaining("private-password");
        assertThatThrownBy(() -> applicator.apply(request(
                List.of(), new RequestAuthentication.JwtBearer("   "))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JWT Bearer authentication requires a token.");
    }

    private HttpRequestDefinition request(
            List<KeyValueEntry> headers,
            RequestAuthentication authentication
    ) {
        return new HttpRequestDefinition(
                UUID.randomUUID(), "Authenticated", HttpMethod.GET, "https://example.com",
                List.of(), headers, RequestBody.none(), authentication);
    }

    private KeyValueEntry entry(String key, String value, boolean enabled) {
        return new KeyValueEntry(UUID.randomUUID(), key, value, enabled);
    }
}
