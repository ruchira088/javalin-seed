package com.ruchij.service.health;

import com.ruchij.service.health.models.BuildInformation;
import com.ruchij.service.health.models.ServiceInformation;
import com.ruchij.utils.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Properties;

public class HealthServiceImpl implements HealthService {
    private final Clock clock;
    private final Properties properties;
    private final BuildInformation buildInformation;

    public HealthServiceImpl(Clock clock, Properties properties, BuildInformation buildInformation) {
        this.clock = clock;
        this.properties = properties;
        this.buildInformation = buildInformation;
    }

    @Override
    public ServiceInformation serviceInformation() {
        String javaVersion = properties.getProperty("java.version", "unknown");
        Instant timestamp = clock.instant();

        ServiceInformation serviceInformation = new ServiceInformation(
            buildInformation.name(),
            javaVersion,
            buildInformation.gradleVersion(),
            timestamp,
            buildInformation.gitBranch(),
            buildInformation.gitCommit(),
            buildInformation.buildTimestamp()
        );

        return serviceInformation;
    }
}
