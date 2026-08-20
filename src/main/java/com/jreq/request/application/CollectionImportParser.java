package com.jreq.request.application;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.shared.exception.ErrorCategory;
import com.jreq.shared.exception.JReqException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Parses and validates the structure of an imported collection document. The mapper used
 * here is deliberately isolated from the shared one: it enforces strict stream
 * constraints (nesting depth, string length) so hostile files cannot exhaust memory
 * or the stack.
 */
public final class CollectionImportParser {
    static final int MAX_IMPORTED_REQUESTS = 500;
    private static final int MAX_NESTING_DEPTH = 64;
    private static final int MAX_STRING_LENGTH = 8 * 1024 * 1024;
    private static final String SUPPORTED_SCHEMA_MARKER = "/json/collection/v2.";

    private final ObjectMapper objectMapper;

    public CollectionImportParser() {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(MAX_NESTING_DEPTH)
                        .maxStringLength(MAX_STRING_LENGTH)
                        .build())
                .build();
        this.objectMapper = new ObjectMapper(factory)
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public CollectionImportDocument parse(byte[] content) {
        Objects.requireNonNull(content, "content");
        CollectionImportDocument document = readDocument(content);
        validate(document);
        return document;
    }

    private CollectionImportDocument readDocument(byte[] content) {
        try {
            return objectMapper.readValue(content, CollectionImportDocument.class);
        } catch (IOException exception) {
            throw invalid("The file is not valid JSON or does not match the collection format.", exception);
        }
    }

    private void validate(CollectionImportDocument document) {
        if (document.info() == null
                || document.info().name() == null
                || document.info().name().isBlank()) {
            throw invalid("The collection file is missing a collection name.", null);
        }
        String schema = document.info().schema();
        if (schema == null
                || !schema.toLowerCase(Locale.ROOT).contains(SUPPORTED_SCHEMA_MARKER)) {
            throw invalid("Only collection v2.0/v2.1 files can be imported.", null);
        }
        int requests = countRequests(document.item() == null ? List.of() : document.item());
        if (requests > MAX_IMPORTED_REQUESTS) {
            throw invalid(
                    "The collection contains " + requests + " requests; the import limit is "
                            + MAX_IMPORTED_REQUESTS + ".",
                    null);
        }
    }

    private int countRequests(List<CollectionImportDocument.CollectionItem> items) {
        int count = 0;
        for (CollectionImportDocument.CollectionItem item : items) {
            if (item == null) {
                continue;
            }
            if (item.item() != null) {
                count += countRequests(item.item());
            } else if (item.request() != null && item.request().isObject()) {
                count++;
            }
        }
        return count;
    }

    private JReqException invalid(String message, Throwable cause) {
        return new JReqException(ErrorCategory.VALIDATION_ERROR, message, cause);
    }
}
