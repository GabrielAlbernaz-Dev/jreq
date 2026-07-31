package com.jreq.request.application;

import com.jreq.shared.exception.ErrorCategory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseInvariantsTest {
    @Test
    void rejectsInvalidSuccessfulResponseMetadata() {
        assertThatThrownBy(() -> success(99, Duration.ZERO, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("statusCode must be between 100 and 599");
        assertThatThrownBy(() -> success(200, Duration.ofNanos(-1), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duration must not be negative");
        assertThatThrownBy(() -> success(200, Duration.ZERO, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("size must not be negative");
    }

    @Test
    void rejectsInvalidFailureAndResolutionMetadata() {
        assertThatThrownBy(() -> new HttpResponseFailure(
                ErrorCategory.TIMEOUT, " ", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userMessage is required");
        assertThatThrownBy(() -> new HttpResponseFailure(
                ErrorCategory.TIMEOUT, "Timeout", Duration.ofNanos(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duration must not be negative");
        assertThatThrownBy(() -> new VariableResolutionStatus(-1, List.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("referenceCount must not be negative");
    }

    @Test
    void rejectsWarningsOnReportsWhoseHistoryWasSaved() {
        HttpResponseFailure failure = new HttpResponseFailure(
                ErrorCategory.TIMEOUT, "Timeout", Duration.ZERO);

        assertThatThrownBy(() -> new ExecutionReport(failure, true, "Unexpected warning"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A saved history result cannot have a warning");
    }

    private HttpResponseSuccess success(int statusCode, Duration duration, long size) {
        return new HttpResponseSuccess(statusCode, Map.of(), new byte[0], duration, size);
    }
}
