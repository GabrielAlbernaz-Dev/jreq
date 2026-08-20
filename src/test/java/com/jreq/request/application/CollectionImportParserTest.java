package com.jreq.request.application;

import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.exception.JReqException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionImportParserTest {
    private static final String SCHEMA_V21 =
            "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";
    private static final String SCHEMA_V20 =
            "https://schema.getpostman.com/json/collection/v2.0.0/collection.json";

    private final CollectionImportParser parser = new CollectionImportParser();

    @Test
    void parsesAMinimalV21Collection() {
        CollectionImportDocument document = parser.parse(json("""
                {"info":{"name":"API","schema":"%s"},"item":[]}
                """, SCHEMA_V21));

        assertThat(document.info().name()).isEqualTo("API");
        assertThat(document.item()).isEmpty();
    }

    @Test
    void acceptsV20CollectionsAndIgnoresUnknownFields() {
        CollectionImportDocument document = parser.parse(json("""
                {
                  "info":{"name":"Legacy","schema":"%s","_postman_id":"abc"},
                  "item":[{"name":"Ping","request":{"method":"GET","url":"https://example.com"},
                           "event":[{"listen":"test"}]}],
                  "protocolProfileBehavior":{"disableBodyPruning":true}
                }
                """, SCHEMA_V20));

        assertThat(document.info().name()).isEqualTo("Legacy");
        assertThat(document.item()).hasSize(1);
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse(json("{ not json")))
                .isInstanceOf(JReqException.class)
                .hasMessageContaining("not valid JSON")
                .extracting(failure -> ((JReqException) failure).category())
                .isEqualTo(ErrorCategory.VALIDATION_ERROR);
    }

    @Test
    void rejectsANonObjectRoot() {
        assertThatThrownBy(() -> parser.parse(json("[1, 2, 3]")))
                .isInstanceOf(JReqException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void rejectsAMissingInfoBlock() {
        assertThatThrownBy(() -> parser.parse(json("{\"item\":[]}")))
                .isInstanceOf(JReqException.class)
                .hasMessageContaining("missing a collection name");
    }

    @Test
    void rejectsABlankCollectionName() {
        assertThatThrownBy(() -> parser.parse(json("""
                {"info":{"name":"   ","schema":"%s"},"item":[]}
                """, SCHEMA_V21)))
                .isInstanceOf(JReqException.class)
                .hasMessageContaining("missing a collection name");
    }

    @Test
    void rejectsAMissingOrUnsupportedSchema() {
        assertThatThrownBy(() -> parser.parse(json("""
                {"info":{"name":"API"},"item":[]}
                """)))
                .isInstanceOf(JReqException.class)
                .hasMessageContaining("v2.0/v2.1");

        assertThatThrownBy(() -> parser.parse(json("""
                {"info":{"name":"API","schema":"https://schema.getpostman.com/json/collection/v1.0.0/collection.json"},"item":[]}
                """)))
                .isInstanceOf(JReqException.class)
                .hasMessageContaining("v2.0/v2.1");
    }

    @Test
    void rejectsCollectionsAboveTheRequestLimit() {
        String items = IntStream.range(0, CollectionImportParser.MAX_IMPORTED_REQUESTS + 1)
                .mapToObj(index -> "{\"name\":\"r" + index + "\",\"request\":{\"method\":\"GET\","
                        + "\"url\":\"https://example.com\"}}")
                .collect(Collectors.joining(","));
        String document = "{\"info\":{\"name\":\"Huge\",\"schema\":\"" + SCHEMA_V21 + "\"},"
                + "\"item\":[" + items + "]}";

        assertThatThrownBy(() -> parser.parse(json(document)))
                .isInstanceOf(JReqException.class)
                .hasMessageContaining("import limit");
    }

    @Test
    void rejectsExcessivelyNestedDocuments() {
        String document = "{\"info\":{\"name\":\"Deep\",\"schema\":\"" + SCHEMA_V21 + "\"},"
                + "\"item\":" + "[".repeat(200) + "]".repeat(200) + "}";

        assertThatThrownBy(() -> parser.parse(json(document)))
                .isInstanceOf(JReqException.class)
                .hasMessageContaining("not valid JSON");
    }

    private byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] json(String template, String schema) {
        return template.formatted(schema).getBytes(StandardCharsets.UTF_8);
    }
}
