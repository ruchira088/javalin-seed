package com.ruchij.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MetricsRegistryTest {

    @Test
    void shouldBindJvmMetricsWithApplicationTag() {
        PrometheusMeterRegistry registry = MetricsRegistry.create("test-app");

        Meter heapUsed = registry.find("jvm.memory.used").meter();
        assertNotNull(heapUsed);
        assertEquals("test-app", heapUsed.getId().getTag(MetricsRegistry.APPLICATION_TAG));

        assertNotNull(registry.find("jvm.threads.live").meter());
        assertNotNull(registry.find("process.uptime").meter());
    }
}
