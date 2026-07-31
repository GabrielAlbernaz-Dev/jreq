package com.jreq.request.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidatedValuesTest {
    @Test
    void createsAnHttpTimeoutThatIsSafeToPassAcrossLayers() {
        HttpTimeout timeout = HttpTimeout.ofSeconds(30);

        assertThat(timeout.value()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void rejectsNonPositiveHttpTimeoutsAtConstruction() {
        assertThatThrownBy(() -> HttpTimeout.of(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HTTP timeout must be positive");
        assertThatThrownBy(() -> HttpTimeout.of(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HTTP timeout must be positive");
    }

    @Test
    void rejectsNonPositiveHistoryLimitsAtConstruction() {
        assertThat(HistoryLimit.of(100).value()).isEqualTo(100);
        assertThatThrownBy(() -> HistoryLimit.of(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("History limit must be positive");
    }
}
