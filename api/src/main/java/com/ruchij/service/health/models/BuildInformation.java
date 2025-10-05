package com.ruchij.service.health.models;

import com.ruchij.build.BuildInfo;

import java.time.Instant;

public record BuildInformation(
    String name,
    String group,
    String version,
    String gradleVersion,
    Instant buildTimestamp,
    String gitBranch,
    String gitCommit
) {
    public static BuildInformation get() {
        return new BuildInformation(
            BuildInfo.APPLICATION_NAME,
            BuildInfo.APPLICATION_GROUP,
            BuildInfo.APPLICATION_VERSION,
            BuildInfo.GRADLE_VERSION,
            BuildInfo.BUILD_TIMESTAMP,
            BuildInfo.GIT_BRANCH,
            BuildInfo.GIT_COMMIT
        );
    }
}
