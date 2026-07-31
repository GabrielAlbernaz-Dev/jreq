package com.jreq.request.domain;

import java.util.Objects;
import java.util.UUID;

public sealed interface EnvironmentSelection permits EnvironmentSelection.None, EnvironmentSelection.Selected {
    static EnvironmentSelection none() {
        return new None();
    }

    static EnvironmentSelection selected(UUID environmentId) {
        return new Selected(environmentId);
    }

    record None() implements EnvironmentSelection {
    }

    record Selected(UUID environmentId) implements EnvironmentSelection {
        public Selected {
            Objects.requireNonNull(environmentId, "environmentId");
        }
    }
}
