package com.ruchij.service.health.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruchij.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceInformationTest {

    @Test
    void shouldCreateServiceInformationRecord() {
        Instant now = Instant.now();
        ServiceInformation info = new ServiceInformation(
            "my-service",
            "21.0.1",
            "8.5",
            now,
            "main",
            "abc123",
            now
        );

        assertEquals("my-service", info.serviceName());
        assertEquals("21.0.1", info.javaVersion());
        assertEquals("8.5", info.gradleVersion());
        assertEquals(now, info.currentTimestamp());
        assertEquals("main", info.gitBranch());
        assertEquals("abc123", info.gitCommit());
        assertEquals(now, info.buildTimestamp());
    }

    @Test
    void shouldSerializeToJson() throws Exception {
        Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
        ServiceInformation info = new ServiceInformation(
            "test-service",
            "21",
            "8.0",
            timestamp,
            "develop",
            "xyz789",
            timestamp
        );

        JsonNode json = JsonUtils.OBJECT_MAPPER.valueToTree(info);

        assertEquals("test-service", json.get("serviceName").asText());
        assertEquals("21", json.get("javaVersion").asText());
        assertEquals("8.0", json.get("gradleVersion").asText());
        assertEquals("2024-01-15T10:30:00Z", json.get("currentTimestamp").asText());
        assertEquals("develop", json.get("gitBranch").asText());
        assertEquals("xyz789", json.get("gitCommit").asText());
    }
}
