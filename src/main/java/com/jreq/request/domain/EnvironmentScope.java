package com.jreq.request.domain;

import java.util.Objects;
import java.util.UUID;

public sealed interface EnvironmentScope permits EnvironmentScope.Global, EnvironmentScope.Collection {
    static EnvironmentScope global() {
        return new Global();
    }

    static EnvironmentScope collection(UUID collectionId) {
        return new Collection(collectionId);
    }

    record Global() implements EnvironmentScope {
    }

    record Collection(UUID collectionId) implements EnvironmentScope {
        public Collection {
            Objects.requireNonNull(collectionId, "collectionId");
        }
    }
}
