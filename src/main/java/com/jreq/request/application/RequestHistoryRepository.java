package com.jreq.request.application;

import com.jreq.request.domain.RequestHistoryEntry;

import java.util.List;
import java.util.UUID;

public interface RequestHistoryRepository {
    void appendAndTrim(RequestHistoryEntry entry, HistoryLimit maximumEntries);

    List<RequestHistoryEntry> findRecent(HistoryLimit limit);

    void deleteById(UUID id);

    void deleteAll();
}
