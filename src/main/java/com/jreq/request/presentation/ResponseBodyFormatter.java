package com.jreq.request.presentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.jreq.request.application.HttpResponseSuccess;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ResponseBodyFormatter {
    private static final String CONTENT_TYPE = "content-type";

    private final ObjectMapper objectMapper;
    private final ObjectWriter prettyWriter;

    public ResponseBodyFormatter(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        Separators separators = Separators.createDefaultInstance()
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER);
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter(separators)
                .withObjectIndenter(indenter)
                .withArrayIndenter(indenter);
        this.prettyWriter = objectMapper.writer(prettyPrinter);
    }

    public String format(HttpResponseSuccess response) {
        return prepare(response).formatted();
    }

    public FormattedBody prepare(HttpResponseSuccess response) {
        Objects.requireNonNull(response, "response");
        String body = response.bodyAsUtf8();
        if (body.isBlank() || !shouldTryJson(response.headers(), body)) {
            return FormattedBody.plain(body);
        }

        try {
            JsonNode json = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(body);
            return FormattedBody.json(body, prettyWriter.writeValueAsString(json));
        } catch (JsonProcessingException ignored) {
            return FormattedBody.plain(body);
        }
    }

    private boolean shouldTryJson(Map<String, List<String>> headers, String body) {
        return hasJsonContentType(headers) || looksLikeStructuredJson(body);
    }

    private boolean hasJsonContentType(Map<String, List<String>> headers) {
        return headers.entrySet().stream()
                .filter(entry -> CONTENT_TYPE.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .map(this::mediaType)
                .anyMatch(type -> type.equals("application/json") || type.endsWith("+json"));
    }

    private String mediaType(String headerValue) {
        int parametersStart = headerValue.indexOf(';');
        String value = parametersStart >= 0
                ? headerValue.substring(0, parametersStart)
                : headerValue;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean looksLikeStructuredJson(String body) {
        String stripped = body.stripLeading();
        return stripped.startsWith("{") || stripped.startsWith("[");
    }

    public record FormattedBody(String original, String formatted, boolean json) {
        public FormattedBody {
            Objects.requireNonNull(original, "original");
            Objects.requireNonNull(formatted, "formatted");
        }

        public static FormattedBody empty() {
            return plain("");
        }

        public static FormattedBody plain(String body) {
            return new FormattedBody(body, body, false);
        }

        public static FormattedBody json(String original, String formatted) {
            return new FormattedBody(original, formatted, true);
        }

        public String displayed(boolean formattingEnabled) {
            return formattingEnabled && json ? formatted : original;
        }
    }
}
