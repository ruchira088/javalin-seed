package com.ruchij.web.middleware;

import com.ruchij.App;
import com.ruchij.service.health.HealthService;
import com.ruchij.service.health.models.ServiceInformation;
import com.ruchij.web.Routes;
import io.javalin.http.Context;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorrelationIdTest {
    private static final ServiceInformation SERVICE_INFORMATION = new ServiceInformation(
        "javalin-seed",
        "25",
        "9.7.1",
        Instant.parse("2023-02-05T04:37:42.566735Z"),
        "main",
        "my-commit",
        Instant.parse("2023-02-05T04:37:42.566735Z")
    );

    @Test
    void shouldEchoCorrelationIdHeaderFromRequest() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation()).thenReturn(SERVICE_INFORMATION);

        JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
            Response response =
                client.get("/service/info", request -> request.header(CorrelationId.HEADER_NAME, "k8s-readiness-probe"));

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
            Response response = client.get("/service/info", request -> request.header(CorrelationId.HEADER_NAME, "   "));

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
            Response response = client.get("/service/info", request -> request.header(CorrelationId.HEADER_NAME, "failing-request"));

            assertEquals(500, response.code());
            assertEquals("failing-request", correlationIdHeader(response));
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
