package com.jreq.request.application;

import com.jreq.request.domain.RequestCollection;
import com.jreq.shared.validation.Constraints;

import java.util.List;
import java.util.Objects;

public record CollectionImportResult(
        RequestCollection collection,
        int importedRequestCount,
        int skippedRequestCount,
        List<ImportWarning> warnings
) {
    public CollectionImportResult {
        Objects.requireNonNull(collection, "collection");
        Constraints.nonNegative(importedRequestCount, "Imported request count must not be negative");
        Constraints.nonNegative(skippedRequestCount, "Skipped request count must not be negative");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public boolean hasNotices() {
        return skippedRequestCount > 0 || !warnings.isEmpty();
    }
}
