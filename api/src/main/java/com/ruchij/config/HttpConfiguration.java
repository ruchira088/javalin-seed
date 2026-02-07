package com.ruchij.config;

import com.typesafe.config.Config;

import java.util.Arrays;
import java.util.List;

public record HttpConfiguration(int port, List<String> allowedOrigins) {
    public static HttpConfiguration parse(Config config) {
        int port = config.getInt("port");

        List<String> allowedOrigins =
            ConfigReaders.optionalConfig(() -> config.getString("allowed-origins"))
                .stream()
                .flatMap(stringValue -> Arrays.stream(stringValue.split(",")))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .toList();

        return new HttpConfiguration(port, allowedOrigins);
    }
}
