package com.ruchij.service.health.models;

import com.ruchij.utils.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

public record BuildInformation(String name, String group, String version, String gradleVersion, Instant buildTimestamp,
                               String gitBranch, String gitCommit) {
    public static BuildInformation create() {
        InputStream inputStream = BuildInformation.class.getClassLoader().getResourceAsStream("build-information.json");

        try {
            if (inputStream != null) {
                BuildInformation buildInformation = JsonUtils.objectMapper.readValue(inputStream, BuildInformation.class);
                return buildInformation;
            }
        } catch (IOException ioException) {
            // Ignore
        }

        return new BuildInformation(
            "javalin-seed",
            "com.ruchij",
            "UNKNOWN",
            "UNKNOWN",
            null,
            "UNKNOWN",
            "UNKNOWN"

        );
    }
}
