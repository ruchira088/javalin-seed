package com.ruchij.web.middleware;

import com.ruchij.App;
import com.ruchij.service.health.HealthService;
import com.ruchij.web.Routes;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestMetricsTest {

    @Test
    void shouldTagRequestsWithRouteTemplateNotRawPath() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Javalin app = Javalin.create(config -> {
            config.routes.get("/users/{id}", context -> context.result("ok"));
            RequestMetrics requestMetrics = new RequestMetrics(registry);
            config.requestLogger.http(requestMetrics::record);
        });

        JavalinTest.test(app, (server, client) -> {
            client.get("/users/1");
            client.get("/users/2");

            Timer timer = awaitTimer(registry, "/users/{id}");
            assertEquals(2, timer.count());
            assertEquals("GET", timer.getId().getTag("method"));
            assertEquals("200", timer.getId().getTag("status"));
            assertEquals("SUCCESS", timer.getId().getTag("outcome"));
        });
    }

    @Test
    void shouldGroupUnmatchedRequestsUnderNotFound() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Javalin app = App.javalin(new Routes(Mockito.mock(HealthService.class)), List.of(), registry);

        JavalinTest.test(app, (server, client) -> {
            client.get("/does-not-exist/123");

            Timer timer = awaitTimer(registry, RequestMetrics.NOT_FOUND_URI);
            assertEquals("404", timer.getId().getTag("status"));
            assertEquals("CLIENT_ERROR", timer.getId().getTag("outcome"));
        });
    }

    // The request logger runs after the response is sent, so the client can observe the response first.
    private static Timer awaitTimer(SimpleMeterRegistry registry, String uri) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            Timer timer = registry.find(RequestMetrics.METRIC_NAME).tag("uri", uri).timer();
            if (timer != null && timer.count() > 0) {
                return timer;
            }
            Thread.sleep(20);
        }
        Timer timer = registry.find(RequestMetrics.METRIC_NAME).tag("uri", uri).timer();
        assertNotNull(timer, "no timer recorded for " + uri);
        assertTrue(timer.count() > 0);
        return timer;
    }
}
