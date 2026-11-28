package com.ruchij.config;

import com.typesafe.config.Config;
import java.util.Arrays;
import java.util.List;

public record HttpConfiguration(
        int port, List<String> allowedOrigins, boolean allowCredentials, boolean apiDocsEnabled) {
    public static HttpConfiguration parse(Config config) {
        int port = config.getInt("port");

        List<String> allowedOrigins = ConfigReaders.optionalConfig(() -> config.getString("allowed-origins")).stream()
                .flatMap(stringValue -> Arrays.stream(stringValue.split(",")))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .toList();

        boolean allowCredentials = ConfigReaders.optionalConfig(() -> config.getBoolean("allow-credentials"))
                .orElse(false);

        boolean apiDocsEnabled = ConfigReaders.optionalConfig(() -> config.getBoolean("api-docs-enabled"))
                .orElse(true);

        return new HttpConfiguration(port, allowedOrigins, allowCredentials, apiDocsEnabled);
    }
}
