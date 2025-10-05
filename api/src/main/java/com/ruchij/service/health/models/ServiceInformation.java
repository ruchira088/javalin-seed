package com.ruchij.service.health.models;

import java.time.Instant;

public record ServiceInformation(
    String serviceName,
    String serviceVersion,
    String javaVersion,
    String gradleVersion,
    Instant currentTimestamp,
    String gitBranch,
    String gitCommit,
    Instant buildTimestamp
) {
}
