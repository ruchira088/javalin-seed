package com.ruchij.service.health.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BuildInformationTest {

    @Test
    void shouldGetBuildInformationFromGeneratedClass() {
        BuildInformation buildInfo = BuildInformation.get();

        assertEquals("javalin-seed", buildInfo.name());
        assertEquals("com.ruchij", buildInfo.group());
        assertNotNull(buildInfo.gradleVersion());
        assertNotNull(buildInfo.buildTimestamp());
        assertNotNull(buildInfo.gitBranch());
        assertNotNull(buildInfo.gitCommit());
    }

    @Test
    void shouldCreateBuildInformationRecord() {
        java.time.Instant timestamp = java.time.Instant.now();
        BuildInformation buildInfo = new BuildInformation(
            "my-app",
            "com.example",
            "8.5",
            timestamp,
            "develop",
            "xyz123"
        );

        assertEquals("my-app", buildInfo.name());
        assertEquals("com.example", buildInfo.group());
        assertEquals("8.5", buildInfo.gradleVersion());
        assertEquals(timestamp, buildInfo.buildTimestamp());
        assertEquals("develop", buildInfo.gitBranch());
        assertEquals("xyz123", buildInfo.gitCommit());
    }
}
