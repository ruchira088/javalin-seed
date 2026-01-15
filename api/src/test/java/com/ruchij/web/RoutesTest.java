package com.ruchij.web;

import com.ruchij.App;
import com.ruchij.service.health.HealthService;
import com.ruchij.service.health.models.ServiceInformation;
import com.ruchij.web.routes.ServiceRoute;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutesTest {

    @Test
    void shouldRegisterServiceRouteUnderServicePath() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation())
            .thenReturn(new ServiceInformation(
                "test-app", "21", "8.0", Instant.now(), "main", "abc", Instant.now()
            ));

        Routes routes = new Routes(healthService);
        Javalin app = App.javalin(routes, List.of());

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/service/info");
            assertEquals(200, response.code());
        });
    }

    @Test
    void shouldAcceptServiceRouteDirectly() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation())
            .thenReturn(new ServiceInformation(
                "test-app", "21", "8.0", Instant.now(), "main", "abc", Instant.now()
            ));

        ServiceRoute serviceRoute = new ServiceRoute(healthService);
        Routes routes = new Routes(serviceRoute);
        Javalin app = App.javalin(routes, List.of());

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/service/info");
            assertEquals(200, response.code());
        });
    }
}
