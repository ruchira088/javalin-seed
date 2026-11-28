package com.ruchij.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpConfigurationTest {

    @Test
    void shouldParsePortFromConfig() {
        Config config = ConfigFactory.parseMap(Map.of("port", 8080));

        HttpConfiguration httpConfig = HttpConfiguration.parse(config);

        assertEquals(8080, httpConfig.port());
        assertTrue(httpConfig.allowedOrigins().isEmpty());
        assertFalse(httpConfig.allowCredentials());
        assertTrue(httpConfig.apiDocsEnabled());
    }

    @Test
    void shouldParseApiDocsEnabled() {
        Config config = ConfigFactory.parseMap(Map.of("port", 8080, "api-docs-enabled", false));

        assertFalse(HttpConfiguration.parse(config).apiDocsEnabled());
    }

    @Test
    void shouldParseAllowCredentials() {
        Config config = ConfigFactory.parseMap(Map.of("port", 8080, "allow-credentials", true));

        assertTrue(HttpConfiguration.parse(config).allowCredentials());
    }

    @Test
    void shouldParseAllowedOriginsFromConfig() {
        Config config = ConfigFactory.parseMap(Map.of(
            "port", 9000,
            "allowed-origins", "https://example.com, https://test.com"
        ));

        HttpConfiguration httpConfig = HttpConfiguration.parse(config);

        assertEquals(9000, httpConfig.port());
        assertEquals(List.of("https://example.com", "https://test.com"), httpConfig.allowedOrigins());
    }

    @Test
    void shouldHandleSingleAllowedOrigin() {
        Config config = ConfigFactory.parseMap(Map.of(
            "port", 8080,
            "allowed-origins", "https://single.com"
        ));

        HttpConfiguration httpConfig = HttpConfiguration.parse(config);

        assertEquals(List.of("https://single.com"), httpConfig.allowedOrigins());
    }

    @Test
    void shouldFilterEmptyOriginsFromList() {
        Config config = ConfigFactory.parseMap(Map.of(
            "port", 8080,
            "allowed-origins", "https://first.com, , https://second.com, "
        ));

        HttpConfiguration httpConfig = HttpConfiguration.parse(config);

        assertEquals(List.of("https://first.com", "https://second.com"), httpConfig.allowedOrigins());
    }

    @Test
    void shouldTrimWhitespaceFromOrigins() {
        Config config = ConfigFactory.parseMap(Map.of(
            "port", 8080,
            "allowed-origins", "  https://first.com  ,  https://second.com  "
        ));

        HttpConfiguration httpConfig = HttpConfiguration.parse(config);

        assertEquals(List.of("https://first.com", "https://second.com"), httpConfig.allowedOrigins());
    }
}
