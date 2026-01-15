package com.ruchij.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonUtilsTest {

    @Test
    void shouldSerializeInstantToIsoString() throws JsonProcessingException {
        Instant instant = Instant.parse("2024-01-15T10:30:00Z");

        String json = JsonUtils.objectMapper.writeValueAsString(instant);

        assertEquals("\"2024-01-15T10:30:00Z\"", json);
    }

    @Test
    void shouldDeserializeIsoStringToInstant() throws JsonProcessingException {
        String json = "\"2024-01-15T10:30:00.123456Z\"";

        Instant instant = JsonUtils.objectMapper.readValue(json, Instant.class);

        assertEquals(Instant.parse("2024-01-15T10:30:00.123456Z"), instant);
    }

    @Test
    void shouldHandleInstantWithNanoseconds() throws JsonProcessingException {
        Instant instant = Instant.parse("2024-01-15T10:30:00.123456789Z");

        String json = JsonUtils.objectMapper.writeValueAsString(instant);
        Instant deserialized = JsonUtils.objectMapper.readValue(json, Instant.class);

        assertEquals(instant, deserialized);
    }

    @Test
    void shouldSerializeRecordWithInstant() throws JsonProcessingException {
        record TestRecord(String name, Instant timestamp) {
        }

        Instant instant = Instant.parse("2024-01-15T10:30:00Z");
        TestRecord record = new TestRecord("test", instant);

        JsonNode json = JsonUtils.objectMapper.valueToTree(record);

        String expectedJson = """
                {
                    "name":"test",
                    "timestamp":"2024-01-15T10:30:00Z"
                }
            """;

        assertEquals(JsonUtils.objectMapper.readTree(expectedJson), json);
    }

    @Test
    void shouldDeserializeRecordWithInstant() throws JsonProcessingException {
        record TestRecord(String name, Instant timestamp) {
        }

        String json = """
            {
                "name":"test",
                "timestamp":"2024-01-15T10:30:00Z"
            }
            """;

        TestRecord record = JsonUtils.objectMapper.readValue(json, TestRecord.class);

        assertEquals(new TestRecord("test", Instant.parse("2024-01-15T10:30:00Z")), record);
    }

    @Test
    void shouldHandleOptionalWithJdk8Module() throws JsonProcessingException {
        record TestRecord(Optional<String> value) {
        }

        TestRecord withValue = new TestRecord(Optional.of("present"));
        JsonNode jsonWithValue = JsonUtils.objectMapper.valueToTree(withValue);

        String expectedJsonWithValue = """
            {
                "value":"present"
            }
            """;

        assertEquals(JsonUtils.objectMapper.readTree(expectedJsonWithValue), jsonWithValue);

        TestRecord withoutValue = new TestRecord(Optional.empty());
        JsonNode jsonWithoutValue = JsonUtils.objectMapper.valueToTree(withoutValue);

        String expectedJsonWithoutValue = """
            {
                "value":null
            }
            """;

        assertEquals(JsonUtils.objectMapper.readTree(expectedJsonWithoutValue), jsonWithoutValue);
    }
}
