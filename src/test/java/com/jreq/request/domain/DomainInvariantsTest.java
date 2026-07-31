package com.jreq.request.domain;

import com.jreq.request.application.EnvironmentConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainInvariantsTest {
    @Test
    void normalizesVariableKeysAndRejectsNegativeDisplayOrders() {
        EnvironmentVariable variable = new EnvironmentVariable(
                UUID.randomUUID(), "  base_url  ", "https://example.com", true, false, 0);

        assertThat(variable.key()).isEqualTo("base_url");
        assertThatThrownBy(() -> new EnvironmentVariable(
                UUID.randomUUID(), "key", "value", true, false, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Variable display order must not be negative");
    }

    @Test
    void rejectsDuplicateVariableKeysInGlobalAndEnvironmentScopes() {
        EnvironmentVariable first = variable("host", 0);
        EnvironmentVariable duplicate = variable("host", 1);

        assertThatThrownBy(() -> new EnvironmentConfiguration(
                List.of(first, duplicate), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate global variable key: host");
        assertThatThrownBy(() -> new RequestEnvironment(
                UUID.randomUUID(),
                "Development",
                EnvironmentScope.global(),
                List.of(first, duplicate),
                Instant.EPOCH,
                Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate variable key: host");
    }

    @Test
    void keepsRequestBodyStateConsistentWithItsType() {
        assertThatThrownBy(() -> new RequestBody(RequestBodyType.NONE, "data", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A NONE body cannot contain data");
        assertThatThrownBy(() -> new RequestBody(RequestBodyType.JSON, "{}", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A request body requires a content type");
    }

    private EnvironmentVariable variable(String key, int order) {
        return new EnvironmentVariable(UUID.randomUUID(), key, "value", true, false, order);
    }
}
