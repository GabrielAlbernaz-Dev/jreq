package com.jreq.request.presentation;

import com.jreq.request.application.HttpResponseSuccess;
import com.jreq.shared.json.JReqObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseBodyFormatterTest {
    private final ResponseBodyFormatter formatter =
            new ResponseBodyFormatter(JReqObjectMapper.create());

    @Test
    void prettyPrintsNestedJsonAndArrays() {
        HttpResponseSuccess response = response(
                "{\"results\":[{\"name\":\"bulbasaur\"},{\"name\":\"ivysaur\"}]}",
                "application/json; charset=utf-8");

        ResponseBodyFormatter.FormattingResult result = autoFormat(response);

        assertThat(result.detectedFormat()).isEqualTo(ResponseBodyFormat.JSON);
        assertThat(result.displayed()).isEqualTo("""
                {
                  "results": [
                    {
                      "name": "bulbasaur"
                    },
                    {
                      "name": "ivysaur"
                    }
                  ]
                }""");
    }

    @Test
    void detectsJsonWithoutAContentTypeAndRecognizesVendorTypes() {
        HttpResponseSuccess missingHeader = response("{\"result\":\"ok\"}", "");
        HttpResponseSuccess vendorType = response(
                "[{\"message\":\"Invalid request\"}]", "application/problem+json");

        assertThat(autoFormat(missingHeader).detectedFormat()).isEqualTo(ResponseBodyFormat.JSON);
        assertThat(autoFormat(vendorType).detectedFormat()).isEqualTo(ResponseBodyFormat.JSON);
    }

    @Test
    void prettyPrintsXmlAndKeepsNestedElementsVisible() {
        HttpResponseSuccess response = response(
                "<?xml version=\"1.0\"?><catalog><!-- kept --><item><name>Pokédex</name></item></catalog>",
                "application/vnd.example+xml");

        ResponseBodyFormatter.FormattingResult result = autoFormat(response);

        assertThat(result.detectedFormat()).isEqualTo(ResponseBodyFormat.XML);
        assertThat(result.displayed())
                .contains("<?xml", "<!-- kept -->", "\n  <item>", "\n    <name>Pokédex</name>");
    }

    @Test
    void blocksXmlDoctypesAndExternalEntities() {
        String xml = """
                <!DOCTYPE root [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <root>&secret;</root>""";
        HttpResponseSuccess response = response(xml, "application/xml");
        ResponseBodyFormatter.PreparedBody prepared = formatter.prepare(response);

        ResponseBodyFormatter.FormattingResult result =
                formatter.format(prepared, ResponseFormattingMode.XML);

        assertThat(result.displayed()).isEqualTo(xml);
        assertThat(result.feedback()).contains("Could not format as XML");
    }

    @Test
    void prettyPrintsHtmlDocumentsAndFragmentsAsSource() {
        HttpResponseSuccess document = response(
                "<!doctype html><html><body><main><p>Hello</p></main></body></html>",
                "text/html");
        HttpResponseSuccess fragment = response(
                "<section><h2>Title</h2><p>Copy</p></section>", "text/plain");

        ResponseBodyFormatter.FormattingResult documentResult = autoFormat(document);
        ResponseBodyFormatter.FormattingResult fragmentResult = formatter.format(
                formatter.prepare(fragment), ResponseFormattingMode.HTML);

        assertThat(documentResult.detectedFormat()).isEqualTo(ResponseBodyFormat.HTML);
        assertThat(documentResult.displayed()).contains("<!doctype html>", "\n<html>", "\n    <main>");
        assertThat(fragmentResult.displayed())
                .startsWith("<section>")
                .contains("\n  <h2>", "\n  <p>")
                .doesNotContain("<html>", "<body>");
    }

    @Test
    void preservesHtmlScriptStyleAndPreformattedContent() {
        HttpResponseSuccess response = response("""
                <html><head><style>.item { color: red; }</style></head><body>
                <pre>line 1
                  line 2</pre><script>const value = "<tag>";</script></body></html>""", "text/html");

        String formatted = autoFormat(response).displayed();

        assertThat(formatted)
                .contains(".item { color: red; }")
                .contains("line 1\n  line 2")
                .contains("const value = \"<tag>\";");
    }

    @Test
    void treatsXhtmlAsHtmlAndFallsBackFromAnIncorrectHeader() {
        HttpResponseSuccess xhtml = response(
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Hi</p></body></html>",
                "application/xhtml+xml");
        HttpResponseSuccess incorrectHeader = response(
                "<!doctype html><html><body><p>Hi</p></body></html>",
                "application/json");

        assertThat(autoFormat(xhtml).detectedFormat()).isEqualTo(ResponseBodyFormat.HTML);
        assertThat(autoFormat(incorrectHeader).detectedFormat()).isEqualTo(ResponseBodyFormat.HTML);
    }

    @Test
    void preservesOriginalAndProvidesFeedbackForAnInvalidOverride() {
        HttpResponseSuccess response = response("Request completed", "text/plain");
        ResponseBodyFormatter.PreparedBody prepared = formatter.prepare(response);

        ResponseBodyFormatter.FormattingResult invalid =
                formatter.format(prepared, ResponseFormattingMode.JSON);
        ResponseBodyFormatter.FormattingResult original =
                formatter.format(prepared, ResponseFormattingMode.ORIGINAL);

        assertThat(invalid.displayed()).isEqualTo("Request completed");
        assertThat(invalid.feedback()).contains("Could not format as JSON");
        assertThat(original.displayed()).isEqualTo("Request completed");
        assertThat(original.feedback()).isEmpty();
    }

    @Test
    void decodesContentTypeCharsetBomAndXmlDeclaration() {
        byte[] latinText = "ação".getBytes(StandardCharsets.ISO_8859_1);
        HttpResponseSuccess latinResponse = response(
                latinText, "text/plain; charset=\"ISO-8859-1\"");
        byte[] bomJson = new byte[] {
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
                '{', '"', 'o', 'k', '"', ':', 't', 'r', 'u', 'e', '}'
        };
        HttpResponseSuccess bomResponse = response(bomJson, "application/json; charset=UTF-8");
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><root>ação</root>";
        HttpResponseSuccess xmlResponse = response(
                xml.getBytes(StandardCharsets.ISO_8859_1), "");

        assertThat(formatter.prepare(latinResponse).original()).isEqualTo("ação");
        assertThat(formatter.prepare(bomResponse).original()).startsWith("{");
        assertThat(formatter.prepare(xmlResponse).original()).contains("ação");
    }

    @Test
    void fallsBackToUtf8WhenCharsetIsInvalid() {
        HttpResponseSuccess response = response(
                "Pokémon".getBytes(StandardCharsets.UTF_8),
                "text/plain; charset=not-a-real-charset");

        assertThat(formatter.prepare(response).original()).isEqualTo("Pokémon");
    }

    @Test
    void keepsMalformedStructuredContentOriginalInAutoMode() {
        String malformed = "{\"result\":true} unexpected";

        ResponseBodyFormatter.FormattingResult result = autoFormat(
                response(malformed, "application/json"));

        assertThat(result.displayed()).isEqualTo(malformed);
        assertThat(result.detectedFormat()).isEqualTo(ResponseBodyFormat.TEXT);
    }

    private ResponseBodyFormatter.FormattingResult autoFormat(HttpResponseSuccess response) {
        return formatter.format(formatter.prepare(response), ResponseFormattingMode.AUTO);
    }

    private HttpResponseSuccess response(String body, String contentType) {
        return response(body.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private HttpResponseSuccess response(byte[] body, String contentType) {
        Map<String, List<String>> headers = contentType.isBlank()
                ? Map.of()
                : Map.of("Content-Type", List.of(contentType));
        return new HttpResponseSuccess(200, headers, body, Duration.ofMillis(12), body.length);
    }
}
