package com.ruchij.web.middleware;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.ruchij.App;
import com.ruchij.service.health.HealthService;
import com.ruchij.web.Routes;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SecurityHeadersTest {

    @Test
    void shouldAddSecurityHeadersToSuccessfulAndErrorResponses() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation()).thenThrow(new IllegalStateException("boom"));

        JavalinTest.test(App.javalin(new Routes(healthService), List.of()), (server, client) -> {
            for (String path : List.of("/service/health", "/service/info", "/does-not-exist", "/swagger")) {
                Response response = client.get(path);

                SecurityHeaders.HEADERS.forEach((name, value) -> {
                    List<String> values = response.headers().get(name);
                    assertNotNull(values, name + " missing on " + path + " (" + response.code() + ")");
                    assertEquals(value, values.getFirst(), name + " on " + path);
                });
            }
        });
    }
}
