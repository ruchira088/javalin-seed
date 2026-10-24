package com.ruchij.web.middleware;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ruchij.App;
import com.ruchij.service.health.HealthService;
import com.ruchij.web.Routes;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLoggingTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogging.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void shouldLogRequestWithCorrelationIdAtInfo() {
        JavalinTest.test(App.javalin(new Routes(Mockito.mock(HealthService.class)), List.of()), (server, client) -> {
            client.get("/service/health", request -> request.header(CorrelationId.HEADER_NAME, "request-log-test"));

            ILoggingEvent event = awaitSingleEvent();
            assertEquals(Level.INFO, event.getLevel());
            assertTrue(
                event.getFormattedMessage().matches("GET /service/health 200 \\d+\\.\\dms"),
                event.getFormattedMessage()
            );
            assertEquals("request-log-test", event.getMDCPropertyMap().get(CorrelationId.MDC_KEY));
        });
    }

    @Test
    void shouldLogKubernetesProbesAtDebug() {
        JavalinTest.test(App.javalin(new Routes(Mockito.mock(HealthService.class)), List.of()), (server, client) -> {
            client.get("/service/health", request -> request.header("User-Agent", "kube-probe/1.31"));

            assertEquals(Level.DEBUG, awaitSingleEvent().getLevel());
        });
    }

    @Test
    void shouldLogUnmatchedRoutes() {
        JavalinTest.test(App.javalin(new Routes(Mockito.mock(HealthService.class)), List.of()), (server, client) -> {
            client.get("/does-not-exist");

            assertTrue(awaitSingleEvent().getFormattedMessage().startsWith("GET /does-not-exist 404 "));
        });
    }

    // The request logger may run just after the response has been flushed to the client, so poll briefly.
    private ILoggingEvent awaitSingleEvent() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(2));

        while (appender.list.isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }

        assertEquals(1, appender.list.size(), appender.list.toString());
        return appender.list.getFirst();
    }
}
