package com.jreq.request.application;

import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestHistoryEntry;
import com.jreq.request.domain.SavedRequest;

import java.util.List;
import java.util.Objects;

public record WorkspaceSnapshot(
        List<RequestCollection> collections,
        List<SavedRequest> savedRequests,
        List<RequestHistoryEntry> history
) {
    public WorkspaceSnapshot {
        collections = List.copyOf(Objects.requireNonNull(collections, "collections"));
        savedRequests = List.copyOf(Objects.requireNonNull(savedRequests, "savedRequests"));
        history = List.copyOf(Objects.requireNonNull(history, "history"));
    }
}
