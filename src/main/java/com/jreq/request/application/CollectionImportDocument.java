package com.jreq.request.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * DTO tree for the supported collection v2.x format.
 * (https://schema.getpostman.com/json/collection/v2.1.0/collection.json).
 * Only the fields jREQ consumes are declared; everything else is ignored.
 * No polymorphic typing is enabled on these types on purpose — the file content
 * is never turned into arbitrary Java objects.
 */
public record CollectionImportDocument(
        CollectionInfo info,
        List<CollectionItem> item,
        ImportAuth auth,
        List<ImportVariable> variable
) {
    public record CollectionInfo(String name, String schema) {
    }

    /**
     * An item is a folder when {@code item} is present, otherwise a request holder.
     * {@code request} stays a {@link JsonNode} because malformed exports occasionally
     * carry a plain string instead of an object.
     */
    public record CollectionItem(String name, List<CollectionItem> item, JsonNode request) {
    }

    public record ImportedRequest(
            String method,
            JsonNode url,
            List<ImportKeyValue> header,
            ImportBody body,
            ImportAuth auth
    ) {
    }

    public record ImportKeyValue(String key, String value, boolean disabled) {
    }

    public record ImportBody(
            String mode,
            String raw,
            List<ImportKeyValue> urlencoded,
            BodyOptions options
    ) {
        public record BodyOptions(RawOptions raw) {
        }

        public record RawOptions(String language) {
        }
    }

    public record ImportAuth(
            String type,
            List<ImportKeyValue> basic,
            List<ImportKeyValue> bearer
    ) {
    }

    /**
     * {@code value} stays a {@link JsonNode} because collection variables can be non-string
     * variable values; they are converted to text during mapping.
     */
    public record ImportVariable(String key, JsonNode value, boolean disabled) {
    }
}
