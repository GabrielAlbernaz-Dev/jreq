package com.jreq.request.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jreq.shared.json.JReqObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestDefinitionJsonTest {
    private final ObjectMapper objectMapper = JReqObjectMapper.create();

    @Test
    void serializesAndDeserializesCompleteRequestDefinition() throws Exception {
        HttpRequestDefinition original = new HttpRequestDefinition(
                UUID.randomUUID(),
                "Create user",
                HttpMethod.POST,
                "https://api.example.com/users",
                List.of(new KeyValueEntry(UUID.randomUUID(), "preview", "true", true)),
                List.of(new KeyValueEntry(UUID.randomUUID(), "Accept", "application/json", true)),
                RequestBody.json("{\"name\":\"Ada\"}")
        );

        String json = objectMapper.writeValueAsString(original);
        HttpRequestDefinition restored = objectMapper.readValue(json, HttpRequestDefinition.class);

        assertThat(restored).isEqualTo(original);
        assertThat(json).contains("Create user", "application/json");
    }
}
