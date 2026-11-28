package com.ruchij.web.middleware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ruchij.App;
import com.ruchij.service.health.HealthService;
import com.ruchij.service.health.models.ServiceInformation;
import com.ruchij.web.Routes;
import io.javalin.http.Context;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Request;
import io.javalin.testtools.Response;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

class CorrelationIdTest {
    private static final ServiceInformation SERVICE_INFORMATION = new ServiceInformation(
            "javalin-seed",
            "25",
            "9.7.1",
            Instant.parse("2023-02-05T04:37:42.566735Z"),
            "main",
            "my-commit",
            Instant.parse("2023-02-05T04:37:42.566735Z"));

    @Test
    void shouldEchoCorrelationIdHeaderFromRequest() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation()).thenReturn(SERVICE_INFORMATION);

        JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
            Response response = client.get(
                    "/service/info", request -> request.header(CorrelationId.HEADER_NAME, "k8s-readiness-probe"));

            assertEquals(200, response.code());
            assertEquals("k8s-readiness-probe", correlationIdHeader(response));
        });
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsAbsent() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation()).thenReturn(SERVICE_INFORMATION);

        JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
            Response response = client.get("/service/info");

            assertEquals(200, response.code());
            String correlationId = correlationIdHeader(response);
            assertNotNull(correlationId);
            assertEquals(correlationId, UUID.fromString(correlationId).toString());
        });
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsBlank() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation()).thenReturn(SERVICE_INFORMATION);

        JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
            Response response =
                    client.get("/service/info", request -> request.header(CorrelationId.HEADER_NAME, "   "));

            String correlationId = correlationIdHeader(response);
            assertNotNull(correlationId);
            assertEquals(correlationId, UUID.fromString(correlationId).toString());
        });
    }

    @Test
    void shouldExposeCorrelationIdInMdcWhileHandlingRequest() {
        HealthService healthService = Mockito.mock(HealthService.class);
        AtomicReference<String> mdcValueDuringRequest = new AtomicReference<>();

        Mockito.when(healthService.serviceInformation()).thenAnswer(invocation -> {
            mdcValueDuringRequest.set(MDC.get(CorrelationId.MDC_KEY));
            return SERVICE_INFORMATION;
        });

        JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
            client.get("/service/info", request -> request.header(CorrelationId.HEADER_NAME, "my-correlation-id"));

            assertEquals("my-correlation-id", mdcValueDuringRequest.get());
        });
    }

    @Test
    void shouldStillEchoCorrelationIdWhenHandlerThrows() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation()).thenThrow(new RuntimeException("boom"));

        JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
            Response response = client.get(
                    "/service/info", request -> request.header(CorrelationId.HEADER_NAME, "failing-request"));

            assertEquals(500, response.code());
            assertEquals("failing-request", correlationIdHeader(response));
        });
    }

    @Test
    void shouldReplaceCorrelationIdContainingUnsafeCharacters() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation()).thenReturn(SERVICE_INFORMATION);

        JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
            Response response = client.get(
                    "/service/info",
                    request ->
                            request.header(CorrelationId.HEADER_NAME, "abc\t2026-09-13 [main] ERROR forged log line"));

            String correlationId = correlationIdHeader(response);
            assertEquals(correlationId, UUID.fromString(correlationId).toString());
        });
    }

    @Test
    void shouldReplaceOverlyLongCorrelationId() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation()).thenReturn(SERVICE_INFORMATION);

        JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
            Response response =
                    client.get("/service/info", request -> request.header(CorrelationId.HEADER_NAME, "a".repeat(65)));

            String correlationId = correlationIdHeader(response);
            assertEquals(correlationId, UUID.fromString(correlationId).toString());
        });
    }

    @Test
    void shouldAcceptCorrelationIdAtMaximumLength() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation()).thenReturn(SERVICE_INFORMATION);

        JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
            String maxLengthId = "a".repeat(64);
            Response response =
                    client.get("/service/info", request -> request.header(CorrelationId.HEADER_NAME, maxLengthId));

            assertEquals(maxLengthId, correlationIdHeader(response));
        });
    }

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    void shouldUseTraceIdFromTraceparentWhenCorrelationHeaderIsAbsent() {
        assertCorrelationIdFor(
                request -> request.header(CorrelationId.TRACEPARENT_HEADER, "00-" + TRACE_ID + "-00f067aa0ba902b7-01"),
                TRACE_ID);
    }

    @Test
    void shouldPreferExplicitCorrelationIdOverTraceparent() {
        assertCorrelationIdFor(
                request -> request.header(CorrelationId.HEADER_NAME, "explicit-id")
                        .header(CorrelationId.TRACEPARENT_HEADER, "00-" + TRACE_ID + "-00f067aa0ba902b7-01"),
                "explicit-id");
    }

    @Test
    void shouldUseTraceparentWhenCorrelationHeaderIsInvalid() {
        assertCorrelationIdFor(
                request -> request.header(CorrelationId.HEADER_NAME, "bad id with spaces")
                        .header(CorrelationId.TRACEPARENT_HEADER, "00-" + TRACE_ID + "-00f067aa0ba902b7-01"),
                TRACE_ID);
    }

    @Test
    void shouldIgnoreMalformedOrAllZeroTraceparent() {
        for (String traceparent : List.of(
                "00-" + TRACE_ID.toUpperCase(Locale.ROOT) + "-00f067aa0ba902b7-01",
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
                "00-" + TRACE_ID + "-0000000000000000-01",
                "ff-" + TRACE_ID + "-00f067aa0ba902b7-01",
                "00-" + TRACE_ID + "-00f067aa0ba902b7",
                "garbage")) {
            HealthService healthService = Mockito.mock(HealthService.class);
            Mockito.when(healthService.serviceInformation()).thenReturn(SERVICE_INFORMATION);

            JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
                Response response = client.get(
                        "/service/info", request -> request.header(CorrelationId.TRACEPARENT_HEADER, traceparent));

                String correlationId = correlationIdHeader(response);
                assertEquals(correlationId, UUID.fromString(correlationId).toString(), traceparent);
            });
        }
    }

    @Test
    void shouldAcceptFutureTraceparentVersionsWithExtraFields() {
        assertCorrelationIdFor(
                request -> request.header(
                        CorrelationId.TRACEPARENT_HEADER, "01-" + TRACE_ID + "-00f067aa0ba902b7-01-extra"),
                TRACE_ID);
    }

    private static void assertCorrelationIdFor(Consumer<Request.Builder> requestCustomizer, String expected) {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation()).thenReturn(SERVICE_INFORMATION);

        JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
            Response response = client.get("/service/info", requestCustomizer);
            assertEquals(expected, correlationIdHeader(response));
        });
    }

    private static String correlationIdHeader(Response response) {
        List<String> values = response.headers().get(CorrelationId.HEADER_NAME);
        assertEquals(1, values.size());
        return values.getFirst();
    }

    @Test
    void shouldClearMdcOnceRequestIsComplete() {
        Context context = Mockito.mock(Context.class);
        Mockito.when(context.header(CorrelationId.HEADER_NAME)).thenReturn("my-correlation-id");

        CorrelationId.start(context);
        assertEquals("my-correlation-id", MDC.get(CorrelationId.MDC_KEY));

        CorrelationId.clear(context);
        assertNull(MDC.get(CorrelationId.MDC_KEY));
    }
}
