package com.jreq.request.presentation;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

final class HtmlResponseContentFormatter implements ResponseContentFormatter {
    private static final Pattern HTML_SIGNATURE = Pattern.compile(
            "(?is)^\\s*(?:<!doctype\\s+html|<html(?:\\s|>)|<head(?:\\s|>)|<body(?:\\s|>)"
                    + "|<(?:a|article|button|div|footer|form|h[1-6]|header|img|input|li|link|main|meta|nav"
                    + "|ol|option|p|script|section|select|span|style|table|title|ul)(?:\\s|>))");
    private static final Pattern HTML_ELEMENT = Pattern.compile("(?is)<[a-z][a-z0-9:-]*(?:\\s|/?>)");

    @Override
    public ResponseBodyFormat format() {
        return ResponseBodyFormat.HTML;
    }

    @Override
    public boolean supports(String mediaType) {
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        return normalized.equals("text/html") || normalized.equals("application/xhtml+xml");
    }

    @Override
    public boolean looksLike(String body) {
        return hasHtmlSignature(body);
    }

    @Override
    public Optional<String> prettyPrint(String body) {
        if (!HTML_ELEMENT.matcher(body).find()) {
            return Optional.empty();
        }

        try {
            Document document = Jsoup.parse(body, "", Parser.htmlParser());
            document.outputSettings()
                    .prettyPrint(true)
                    .outline(true)
                    .indentAmount(2);
            return Optional.of(isFullDocument(body)
                    ? document.outerHtml().stripTrailing()
                    : document.body().html().stripTrailing());
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static boolean hasHtmlSignature(String body) {
        return HTML_SIGNATURE.matcher(body).find();
    }

    private boolean isFullDocument(String body) {
        String stripped = body.stripLeading().toLowerCase(Locale.ROOT);
        return stripped.startsWith("<!doctype html")
                || stripped.startsWith("<html")
                || stripped.startsWith("<head")
                || stripped.startsWith("<body");
    }
}
