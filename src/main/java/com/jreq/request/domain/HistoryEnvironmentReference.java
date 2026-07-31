package com.jreq.request.domain;

import java.util.Objects;
import java.util.UUID;

public sealed interface HistoryEnvironmentReference
        permits HistoryEnvironmentReference.None, HistoryEnvironmentReference.Selected {
    static HistoryEnvironmentReference none() {
        return new None();
    }

    static HistoryEnvironmentReference selected(UUID id, String name, EnvironmentScope scope) {
        return new Selected(id, name, scope);
    }

    record None() implements HistoryEnvironmentReference {
    }

    record Selected(UUID id, String name, EnvironmentScope scope) implements HistoryEnvironmentReference {
        public Selected {
            Objects.requireNonNull(id, "id");
            name = WorkspaceName.require(name);
            Objects.requireNonNull(scope, "scope");
        }
    }
}
