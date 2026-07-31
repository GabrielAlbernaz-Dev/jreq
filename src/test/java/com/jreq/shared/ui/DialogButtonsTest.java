package com.jreq.shared.ui;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DialogButtonsTest {
    @Test
    void usesEnglishCancelLabelWithCancelSemantics() {
        ButtonType cancel = DialogButtons.cancel();

        assertThat(cancel.getText()).isEqualTo("Cancel");
        assertThat(cancel.getButtonData()).isEqualTo(ButtonBar.ButtonData.CANCEL_CLOSE);
    }

    @Test
    void usesEnglishOkLabelWithConfirmationSemantics() {
        ButtonType ok = DialogButtons.ok();

        assertThat(ok.getText()).isEqualTo("OK");
        assertThat(ok.getButtonData()).isEqualTo(ButtonBar.ButtonData.OK_DONE);
    }
}
