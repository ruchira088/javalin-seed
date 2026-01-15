package com.ruchij.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationConfigurationTest {

    @Test
    void shouldParseApplicationConfiguration() {
        Config config = ConfigFactory.parseMap(Map.of(
            "http.port", 8080
        ));

        ApplicationConfiguration appConfig = ApplicationConfiguration.parse(config);

        assertEquals(8080, appConfig.httpConfiguration().port());
    }

    @Test
    void shouldParseFullConfiguration() {
        Config config = ConfigFactory.parseMap(Map.of(
            "http.port", 9000,
            "http.allowed-origins", "https://example.com"
        ));

        ApplicationConfiguration appConfig = ApplicationConfiguration.parse(config);

        assertEquals(9000, appConfig.httpConfiguration().port());
        assertEquals(List.of("https://example.com"), appConfig.httpConfiguration().allowedOrigins());
    }
}
