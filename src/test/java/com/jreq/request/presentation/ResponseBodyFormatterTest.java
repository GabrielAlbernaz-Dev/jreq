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
    void prettyPrintsJsonResponses() {
        HttpResponseSuccess response = response(
                "{\"name\":\"Ada\",\"details\":{\"active\":true}}",
                Map.of("content-type", List.of("application/json; charset=utf-8")));

        assertThat(formatter.format(response)).isEqualTo("""
                {
                  "name": "Ada",
                  "details": {
                    "active": true
                  }
                }""");
    }

    @Test
    void recognizesJsonVendorMediaTypesCaseInsensitively() {
        HttpResponseSuccess response = response(
                "[{\"message\":\"Invalid request\"}]",
                Map.of("Content-Type", List.of("Application/Problem+Json")));

        assertThat(formatter.format(response)).contains("\n");
    }

    @Test
    void recognizesStructuredJsonWhenContentTypeIsMissing() {
        HttpResponseSuccess response = response(
                "  {\"result\":\"ok\"}",
                Map.of());

        assertThat(formatter.format(response)).isEqualTo("""
                {
                  "result": "ok"
                }""");
    }

    @Test
    void placesArrayItemsOnIndentedLinesBelowTheirParent() {
        HttpResponseSuccess response = response(
                "{\"results\":[{\"name\":\"bulbasaur\"},{\"name\":\"ivysaur\"}]}",
                Map.of("content-type", List.of("application/json")));

        assertThat(formatter.format(response)).isEqualTo("""
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
    void preservesPlainTextAndMalformedJson() {
        HttpResponseSuccess plainText = response("Request completed", Map.of());
        HttpResponseSuccess malformedJson = response(
                "{\"result\":true} unexpected",
                Map.of("content-type", List.of("application/json")));

        assertThat(formatter.format(plainText)).isEqualTo("Request completed");
        assertThat(formatter.format(malformedJson)).isEqualTo("{\"result\":true} unexpected");
    }

    private HttpResponseSuccess response(String body, Map<String, List<String>> headers) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return new HttpResponseSuccess(200, headers, bytes, Duration.ofMillis(12), bytes.length);
    }
}
