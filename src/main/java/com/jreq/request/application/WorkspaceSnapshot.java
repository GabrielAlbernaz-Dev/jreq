package com.jreq.request.application;

import com.jreq.request.domain.RequestCollection;
import com.jreq.request.domain.RequestHistoryEntry;
import com.jreq.request.domain.SavedRequest;
import com.jreq.request.domain.StoredCookie;

import java.util.List;
import java.util.Objects;

public record WorkspaceSnapshot(
        List<RequestCollection> collections,
        List<SavedRequest> savedRequests,
        List<RequestHistoryEntry> history,
        EnvironmentConfiguration environmentConfiguration,
        List<EnvironmentActivation> environmentActivations,
        List<StoredCookie> cookies
) {
    public WorkspaceSnapshot {
        collections = List.copyOf(Objects.requireNonNull(collections, "collections"));
        savedRequests = List.copyOf(Objects.requireNonNull(savedRequests, "savedRequests"));
        history = List.copyOf(Objects.requireNonNull(history, "history"));
        Objects.requireNonNull(environmentConfiguration, "environmentConfiguration");
        environmentActivations = List.copyOf(Objects.requireNonNull(
                environmentActivations, "environmentActivations"));
        cookies = List.copyOf(Objects.requireNonNull(cookies, "cookies"));
    }

    public WorkspaceSnapshot(
            List<RequestCollection> collections,
            List<SavedRequest> savedRequests,
            List<RequestHistoryEntry> history,
            EnvironmentConfiguration environmentConfiguration,
            List<EnvironmentActivation> environmentActivations
    ) {
        this(collections, savedRequests, history, environmentConfiguration, environmentActivations, List.of());
    }

    public WorkspaceSnapshot(
            List<RequestCollection> collections,
            List<SavedRequest> savedRequests,
            List<RequestHistoryEntry> history
    ) {
        this(collections, savedRequests, history, EnvironmentConfiguration.empty(), List.of(), List.of());
    }
}
