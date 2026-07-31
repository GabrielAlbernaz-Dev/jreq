package com.jreq.request.presentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class JsonResponseContentFormatter implements ResponseContentFormatter {
    private final ObjectReader strictReader;
    private final ObjectWriter prettyWriter;

    JsonResponseContentFormatter(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        strictReader = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        Separators separators = Separators.createDefaultInstance()
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER);
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter(separators)
                .withObjectIndenter(indenter)
                .withArrayIndenter(indenter);
        prettyWriter = objectMapper.writer(prettyPrinter);
    }

    @Override
    public ResponseBodyFormat format() {
        return ResponseBodyFormat.JSON;
    }

    @Override
    public boolean supports(String mediaType) {
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        return normalized.equals("application/json") || normalized.endsWith("+json");
    }

    @Override
    public boolean looksLike(String body) {
        String stripped = body.stripLeading();
        return stripped.startsWith("{") || stripped.startsWith("[");
    }

    @Override
    public Optional<String> prettyPrint(String body) {
        try {
            JsonNode json = strictReader.readTree(body);
            return Optional.of(prettyWriter.writeValueAsString(json));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }
}
