package com.jreq.shared.ui;

import com.jreq.request.domain.KeyValueEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class KeyValueEditorModel {
    private final List<KeyValueEntry> entries = new ArrayList<>();

    public KeyValueEditorModel() {
        entries.add(KeyValueEntry.empty());
    }

    public List<KeyValueEntry> entries() {
        return List.copyOf(entries);
    }

    public KeyValueEntry addEntry() {
        KeyValueEntry entry = KeyValueEntry.empty();
        entries.add(entry);
        return entry;
    }

    public void replaceEntries(List<KeyValueEntry> replacement) {
        Objects.requireNonNull(replacement, "replacement");
        entries.clear();
        entries.addAll(replacement);
        if (entries.isEmpty()) {
            entries.add(KeyValueEntry.empty());
        }
    }

    public void update(UUID id, String key, String value, boolean enabled) {
        Objects.requireNonNull(id, "id");
        for (int index = 0; index < entries.size(); index++) {
            KeyValueEntry entry = entries.get(index);
            if (entry.id().equals(id)) {
                entries.set(index, entry.withValues(key, value, enabled));
                return;
            }
        }
        throw new IllegalArgumentException("Unknown key-value entry: " + id);
    }

    public void remove(UUID id) {
        Objects.requireNonNull(id, "id");
        entries.removeIf(entry -> entry.id().equals(id));
        if (entries.isEmpty()) {
            entries.add(KeyValueEntry.empty());
        }
    }
}
