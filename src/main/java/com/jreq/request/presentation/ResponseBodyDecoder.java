package com.jreq.request.presentation;

import com.jreq.request.application.HttpResponseSuccess;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ResponseBodyDecoder {
    private static final String CONTENT_TYPE = "content-type";
    private static final Pattern CHARSET_PARAMETER = Pattern.compile(
            "(?i)(?:^|;)\\s*charset\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^;\\s]+))");
    private static final Pattern XML_ENCODING = Pattern.compile(
            "(?i)<\\?xml[^>]*\\bencoding\\s*=\\s*['\"]([^'\"]+)['\"]");

    DecodedResponseBody decode(HttpResponseSuccess response) {
        String contentType = headerValue(response.headers(), CONTENT_TYPE).orElse("");
        String mediaType = mediaType(contentType);
        byte[] body = response.body();

        CharsetSelection selection = charsetFromContentType(contentType)
                .map(selected -> withMatchingBomOffset(selected, body))
                .or(() -> charsetFromBom(body))
                .or(() -> charsetFromXmlDeclaration(body, mediaType))
                .orElse(new CharsetSelection(StandardCharsets.UTF_8, 0));
        String text = new String(
                body,
                selection.byteOffset(),
                body.length - selection.byteOffset(),
                selection.charset());
        return new DecodedResponseBody(text, mediaType);
    }

    private Optional<String> headerValue(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst();
    }

    private String mediaType(String contentType) {
        int parametersStart = contentType.indexOf(';');
        String value = parametersStart >= 0
                ? contentType.substring(0, parametersStart)
                : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Optional<CharsetSelection> charsetFromContentType(String contentType) {
        Matcher matcher = CHARSET_PARAMETER.matcher(contentType);
        if (!matcher.find()) {
            return Optional.empty();
        }
        for (int group = 1; group <= matcher.groupCount(); group++) {
            if (matcher.group(group) != null) {
                return charset(matcher.group(group), 0);
            }
        }
        return Optional.empty();
    }

    private Optional<CharsetSelection> charsetFromBom(byte[] body) {
        if (startsWith(body, 0x00, 0x00, 0xFE, 0xFF)) {
            return charset("UTF-32BE", 4);
        }
        if (startsWith(body, 0xFF, 0xFE, 0x00, 0x00)) {
            return charset("UTF-32LE", 4);
        }
        if (startsWith(body, 0xEF, 0xBB, 0xBF)) {
            return Optional.of(new CharsetSelection(StandardCharsets.UTF_8, 3));
        }
        if (startsWith(body, 0xFE, 0xFF)) {
            return Optional.of(new CharsetSelection(StandardCharsets.UTF_16BE, 2));
        }
        if (startsWith(body, 0xFF, 0xFE)) {
            return Optional.of(new CharsetSelection(StandardCharsets.UTF_16LE, 2));
        }
        return Optional.empty();
    }

    private CharsetSelection withMatchingBomOffset(CharsetSelection selection, byte[] body) {
        String charset = selection.charset().name().toUpperCase(Locale.ROOT);
        if (charset.equals("UTF-8") && startsWith(body, 0xEF, 0xBB, 0xBF)) {
            return new CharsetSelection(selection.charset(), 3);
        }
        if (charset.equals("UTF-16BE") && startsWith(body, 0xFE, 0xFF)) {
            return new CharsetSelection(selection.charset(), 2);
        }
        if (charset.equals("UTF-16LE") && startsWith(body, 0xFF, 0xFE)) {
            return new CharsetSelection(selection.charset(), 2);
        }
        if (charset.equals("UTF-32BE") && startsWith(body, 0x00, 0x00, 0xFE, 0xFF)) {
            return new CharsetSelection(selection.charset(), 4);
        }
        if (charset.equals("UTF-32LE") && startsWith(body, 0xFF, 0xFE, 0x00, 0x00)) {
            return new CharsetSelection(selection.charset(), 4);
        }
        return selection;
    }

    private Optional<CharsetSelection> charsetFromXmlDeclaration(byte[] body, String mediaType) {
        int prefixLength = Math.min(body.length, 512);
        String prefix = new String(body, 0, prefixLength, StandardCharsets.ISO_8859_1);
        boolean xmlMediaType = mediaType.equals("application/xml")
                || mediaType.equals("text/xml")
                || mediaType.endsWith("+xml");
        if (!xmlMediaType && !prefix.stripLeading().startsWith("<?xml")) {
            return Optional.empty();
        }
        Matcher matcher = XML_ENCODING.matcher(prefix);
        return matcher.find() ? charset(matcher.group(1), 0) : Optional.empty();
    }

    private Optional<CharsetSelection> charset(String name, int byteOffset) {
        try {
            return Optional.of(new CharsetSelection(Charset.forName(name.trim()), byteOffset));
        } catch (IllegalCharsetNameException | UnsupportedCharsetException ignored) {
            return Optional.empty();
        }
    }

    private boolean startsWith(byte[] body, int... prefix) {
        if (body.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (Byte.toUnsignedInt(body[index]) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    record DecodedResponseBody(String text, String mediaType) {
    }

    private record CharsetSelection(Charset charset, int byteOffset) {
    }
}
