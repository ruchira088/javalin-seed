package com.ruchij.config;

import com.typesafe.config.ConfigException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigReadersTest {

    @Test
    void shouldReturnValueWhenPresent() {
        Optional<String> result = ConfigReaders.optionalConfig(() -> "test-value");

        assertTrue(result.isPresent());
        assertEquals("test-value", result.get());
    }

    @Test
    void shouldReturnEmptyWhenConfigMissing() {
        Optional<String> result = ConfigReaders.optionalConfig(() -> {
            throw new ConfigException.Missing("test.path");
        });

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullValue() {
        Optional<String> result = ConfigReaders.optionalConfig(() -> null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldWorkWithDifferentTypes() {
        Optional<Integer> intResult = ConfigReaders.optionalConfig(() -> 42);
        assertTrue(intResult.isPresent());
        assertEquals(42, intResult.get());

        Optional<Boolean> boolResult = ConfigReaders.optionalConfig(() -> true);
        assertTrue(boolResult.isPresent());
        assertEquals(true, boolResult.get());
    }
}
