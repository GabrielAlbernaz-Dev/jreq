package com.jreq.request.presentation;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class MainFxmlStructureTest {
    @Test
    void keepsRequiredWorkspaceControlsInWellFormedFxml() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try (InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream("/fxml/main-view.fxml"))) {
            byte[] bytes = input.readAllBytes();
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            String source = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

            assertThat(document.getDocumentElement().getLocalName()).isEqualTo("BorderPane");
            assertThat(source).contains(
                    "fx:id=\"rootRequests\"",
                    "fx:id=\"collectionsList\"",
                    "fx:id=\"historyList\"",
                    "fx:id=\"paramsEditor\"",
                    "fx:id=\"headersEditor\"",
                    "fx:id=\"bodyTypeSelector\"",
                    "fx:id=\"variableFeedbackLabel\"",
                    "fx:id=\"responseHeaders\"",
                    "fx:id=\"responseRaw\"");
            assertThat(source).contains("COLLECTIONS", "HISTORY", "Response");
        }
    }
}
