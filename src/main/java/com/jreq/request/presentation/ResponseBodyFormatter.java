package com.jreq.request.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.request.application.HttpResponseSuccess;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ResponseBodyFormatter {
    private final ResponseBodyDecoder decoder = new ResponseBodyDecoder();
    private final List<ResponseContentFormatter> formatters;
    private final Map<ResponseBodyFormat, ResponseContentFormatter> formatterByType;

    public ResponseBodyFormatter(ObjectMapper objectMapper) {
        formatters = List.of(
                new JsonResponseContentFormatter(objectMapper),
                new XmlResponseContentFormatter(),
                new HtmlResponseContentFormatter());
        formatterByType = new EnumMap<>(ResponseBodyFormat.class);
        formatters.forEach(formatter -> formatterByType.put(formatter.format(), formatter));
    }

    public PreparedBody prepare(HttpResponseSuccess response) {
        Objects.requireNonNull(response, "response");
        ResponseBodyDecoder.DecodedResponseBody decoded = decoder.decode(response);
        return new PreparedBody(
                decoded.text(),
                hintedFormat(decoded.mediaType()));
    }

    public FormattingResult format(PreparedBody body, ResponseFormattingMode mode) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(mode, "mode");
        if (mode == ResponseFormattingMode.ORIGINAL || body.original().isBlank()) {
            return FormattingResult.original(body.original(), body.hintedFormat());
        }
        if (mode == ResponseFormattingMode.AUTO) {
            return autoFormat(body);
        }

        ResponseBodyFormat requestedFormat = formatFor(mode);
        return attempt(body.original(), formatterByType.get(requestedFormat))
                .orElseGet(() -> FormattingResult.invalid(
                        body.original(),
                        requestedFormat,
                        "Could not format as " + requestedFormat + "; showing original."));
    }

    public FormattingResult failed(PreparedBody body, ResponseFormattingMode mode) {
        ResponseBodyFormat format = mode == ResponseFormattingMode.AUTO
                ? body.hintedFormat()
                : formatFor(mode);
        return FormattingResult.invalid(
                body.original(), format, "Unable to format the response; showing original.");
    }

    private FormattingResult autoFormat(PreparedBody body) {
        if (body.hintedFormat() != ResponseBodyFormat.TEXT) {
            Optional<FormattingResult> hinted = attempt(
                    body.original(), formatterByType.get(body.hintedFormat()));
            if (hinted.isPresent()) {
                return hinted.get();
            }
        }

        for (ResponseContentFormatter formatter : formatters) {
            if (formatter.format() != body.hintedFormat() && formatter.looksLike(body.original())) {
                Optional<FormattingResult> detected = attempt(body.original(), formatter);
                if (detected.isPresent()) {
                    return detected.get();
                }
            }
        }
        return FormattingResult.original(body.original(), ResponseBodyFormat.TEXT);
    }

    private Optional<FormattingResult> attempt(String original, ResponseContentFormatter formatter) {
        if (formatter == null) {
            return Optional.empty();
        }
        return formatter.prettyPrint(original)
                .map(formatted -> FormattingResult.formatted(original, formatted, formatter.format()));
    }

    private ResponseBodyFormat hintedFormat(String mediaType) {
        return formatters.stream()
                .filter(formatter -> formatter.supports(mediaType))
                .map(ResponseContentFormatter::format)
                .findFirst()
                .orElse(ResponseBodyFormat.TEXT);
    }

    private ResponseBodyFormat formatFor(ResponseFormattingMode mode) {
        return switch (mode) {
            case JSON -> ResponseBodyFormat.JSON;
            case XML -> ResponseBodyFormat.XML;
            case HTML -> ResponseBodyFormat.HTML;
            case AUTO, ORIGINAL -> ResponseBodyFormat.TEXT;
        };
    }

    public record PreparedBody(String original, ResponseBodyFormat hintedFormat) {
        public PreparedBody {
            Objects.requireNonNull(original, "original");
            Objects.requireNonNull(hintedFormat, "hintedFormat");
        }

        public static PreparedBody empty() {
            return new PreparedBody("", ResponseBodyFormat.TEXT);
        }
    }

    public record FormattingResult(
            String original,
            String displayed,
            ResponseBodyFormat detectedFormat,
            boolean formatted,
            String feedback
    ) {
        public FormattingResult {
            Objects.requireNonNull(original, "original");
            Objects.requireNonNull(displayed, "displayed");
            Objects.requireNonNull(detectedFormat, "detectedFormat");
            Objects.requireNonNull(feedback, "feedback");
        }

        public static FormattingResult formatted(
                String original,
                String displayed,
                ResponseBodyFormat detectedFormat
        ) {
            return new FormattingResult(original, displayed, detectedFormat, true, "");
        }

        public static FormattingResult original(String original, ResponseBodyFormat detectedFormat) {
            return new FormattingResult(original, original, detectedFormat, false, "");
        }

        public static FormattingResult invalid(
                String original,
                ResponseBodyFormat detectedFormat,
                String feedback
        ) {
            return new FormattingResult(original, original, detectedFormat, false, feedback);
        }
    }
}
