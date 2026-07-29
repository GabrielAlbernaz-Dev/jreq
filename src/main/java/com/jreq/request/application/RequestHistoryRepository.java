package com.jreq.request.application;

import com.jreq.request.domain.RequestHistoryEntry;

import java.util.List;
import java.util.UUID;

public interface RequestHistoryRepository {
    void appendAndTrim(RequestHistoryEntry entry, int maximumEntries);

    List<RequestHistoryEntry> findRecent(int limit);

    void deleteById(UUID id);

    void deleteAll();
}
