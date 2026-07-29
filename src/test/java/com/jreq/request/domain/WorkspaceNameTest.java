package com.jreq.request.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceNameTest {
    @Test
    void trimsAndCreatesALocaleIndependentComparisonKey() {
        assertThat(WorkspaceName.require("  Status  ")).isEqualTo("Status");
        assertThat(WorkspaceName.comparisonKey("  STATUS  ")).isEqualTo("status");
    }

    @Test
    void rejectsBlankNames() {
        assertThatThrownBy(() -> WorkspaceName.require("  \t "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name is required.");
    }
}
