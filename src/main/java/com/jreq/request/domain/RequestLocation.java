package com.jreq.request.domain;

import java.util.Objects;
import java.util.UUID;

public sealed interface RequestLocation permits RequestLocation.Root, RequestLocation.Collection {
    static RequestLocation root() {
        return new Root();
    }

    static RequestLocation collection(UUID collectionId) {
        return new Collection(collectionId);
    }

    record Root() implements RequestLocation {
    }

    record Collection(UUID collectionId) implements RequestLocation {
        public Collection {
            Objects.requireNonNull(collectionId, "collectionId");
        }
    }
}
