package com.jreq.shared.ui;

import com.jreq.request.domain.KeyValueEntry;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeyValueEditorModelTest {
    @Test
    void addsUpdatesAndRemovesEntriesWhileKeepingOneEditableRow() {
        KeyValueEditorModel model = new KeyValueEditorModel();
        KeyValueEntry initial = model.entries().getFirst();

        model.update(initial.id(), "Accept", "application/json", false);
        KeyValueEntry second = model.addEntry();

        assertThat(model.entries()).hasSize(2);
        assertThat(model.entries().getFirst())
                .extracting(KeyValueEntry::key, KeyValueEntry::value, KeyValueEntry::enabled)
                .containsExactly("Accept", "application/json", false);

        model.remove(initial.id());
        model.remove(second.id());

        assertThat(model.entries()).hasSize(1);
        assertThat(model.entries().getFirst().id()).isNotEqualTo(UUID.fromString(
                "00000000-0000-0000-0000-000000000000"));
        assertThat(model.entries().getFirst().key()).isEmpty();
    }

    @Test
    void replacesRowsWhenLoadingARequest() {
        KeyValueEditorModel model = new KeyValueEditorModel();
        KeyValueEntry loaded = new KeyValueEntry(
                UUID.randomUUID(), "Accept", "application/json", true);

        model.replaceEntries(List.of(loaded));

        assertThat(model.entries()).containsExactly(loaded);
    }
}
