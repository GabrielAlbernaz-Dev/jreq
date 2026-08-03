package com.jreq.request.presentation;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
                    "fx:id=\"authenticationEditor\"",
                    "fx:id=\"bodyTypeSelector\"",
                    "fx:id=\"variableFeedbackLabel\"",
                    "fx:id=\"responseBodyToolbar\"",
                    "fx:id=\"responseFormatSelector\"",
                    "fx:id=\"responseHeaders\"",
                    "fx:id=\"responseRaw\"");
            assertThat(source).contains("COLLECTIONS", "HISTORY", "Response");

            Element formatSelector = findByFxId(document, "responseFormatSelector");
            assertThat(formatSelector)
                    .as("the response format selector must remain scoped to the Body tab")
                    .matches(element -> hasAncestorTab(element, "Body"));
            assertThat(findByFxId(document, "authenticationEditor"))
                    .matches(element -> hasAncestorTab(element, "Auth"));
        }
    }

    private Element findByFxId(Document document, String fxId) {
        NodeList elements = document.getElementsByTagName("*");
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            if (fxId.equals(element.getAttribute("fx:id"))) {
                return element;
            }
        }
        throw new AssertionError("Missing FXML element with fx:id=" + fxId);
    }

    private boolean hasAncestorTab(Element element, String tabText) {
        Node current = element.getParentNode();
        while (current != null) {
            if (current instanceof Element ancestor
                    && "Tab".equals(ancestor.getTagName())
                    && tabText.equals(ancestor.getAttribute("text"))) {
                return true;
            }
            current = current.getParentNode();
        }
        return false;
    }
}
