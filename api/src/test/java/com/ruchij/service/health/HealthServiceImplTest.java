package com.ruchij.service.health;

import com.ruchij.service.health.models.BuildInformation;
import com.ruchij.service.health.models.ServiceInformation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthServiceImplTest {

    @Test
    void shouldReturnServiceInformationWithAllFields() {
        Instant fixedInstant = Instant.parse("2024-01-15T10:30:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        Properties properties = new Properties();
        properties.setProperty("java.version", "21.0.1");

        Instant buildTimestamp = Instant.parse("2024-01-14T08:00:00Z");
        BuildInformation buildInformation = new BuildInformation(
            "test-app",
            "com.test",
            "8.5",
            buildTimestamp,
            "feature-branch",
            "abc123"
        );

        HealthServiceImpl healthService = new HealthServiceImpl(fixedClock, properties, buildInformation);

        ServiceInformation result = healthService.serviceInformation();

        assertEquals("test-app", result.serviceName());
        assertEquals("21.0.1", result.javaVersion());
        assertEquals("8.5", result.gradleVersion());
        assertEquals(fixedInstant, result.currentTimestamp());
        assertEquals("feature-branch", result.gitBranch());
        assertEquals("abc123", result.gitCommit());
        assertEquals(buildTimestamp, result.buildTimestamp());
    }

    @Test
    void shouldReturnUnknownWhenJavaVersionMissing() {
        Instant fixedInstant = Instant.parse("2024-01-15T10:30:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        Properties properties = new Properties();

        BuildInformation buildInformation = new BuildInformation(
            "test-app",
            "com.test",
            "8.5",
            fixedInstant,
            "main",
            "def456"
        );

        HealthServiceImpl healthService = new HealthServiceImpl(fixedClock, properties, buildInformation);

        ServiceInformation result = healthService.serviceInformation();

        assertEquals("unknown", result.javaVersion());
    }

    @Test
    void shouldUseCurrentTimestampFromClock() {
        Instant firstInstant = Instant.parse("2024-01-15T10:00:00Z");
        Instant secondInstant = Instant.parse("2024-01-15T11:00:00Z");

        Properties properties = new Properties();
        properties.setProperty("java.version", "21");

        BuildInformation buildInformation = new BuildInformation(
            "test-app",
            "com.test",
            "8.5",
            firstInstant,
            "main",
            "xyz789"
        );

        Clock firstClock = Clock.fixed(firstInstant, ZoneId.of("UTC"));
        HealthServiceImpl healthService1 = new HealthServiceImpl(firstClock, properties, buildInformation);
        assertEquals(firstInstant, healthService1.serviceInformation().currentTimestamp());

        Clock secondClock = Clock.fixed(secondInstant, ZoneId.of("UTC"));
        HealthServiceImpl healthService2 = new HealthServiceImpl(secondClock, properties, buildInformation);
        assertEquals(secondInstant, healthService2.serviceInformation().currentTimestamp());
    }
}
