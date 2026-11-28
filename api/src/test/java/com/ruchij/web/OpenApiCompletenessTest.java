package com.ruchij.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruchij.App;
import com.ruchij.service.health.HealthService;
import com.ruchij.testing.JavalinTestSupport;
import com.ruchij.utils.JsonUtils;
import io.javalin.Javalin;
import io.javalin.router.Endpoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Guards against handlers that are routed but missing from {@code /openapi.json}. The spec is generated at compile time
 * from {@code @OpenApi} annotations, so a handler without one (or with a path that has drifted from its route) is
 * silently undocumented; this test fails instead.
 */
class OpenApiCompletenessTest {
    // Registered by the OpenAPI and Swagger plugins themselves, not by Routes.
    private static final List<String> PLUGIN_PATH_PREFIXES = List.of("/openapi", "/swagger", "/webjars");

    @Test
    void shouldDocumentEveryRegisteredHttpEndpoint() {
        Javalin app = App.javalin(new Routes(Mockito.mock(HealthService.class)), List.of());

        JavalinTestSupport.test(app, (server, client) -> {
            Set<String> routed = new TreeSet<>();
            for (var parsedEndpoint : app.unsafe.internalRouter.allHttpHandlers()) {
                Endpoint endpoint = parsedEndpoint.endpoint;
                boolean pluginOwned = PLUGIN_PATH_PREFIXES.stream().anyMatch(endpoint.path::startsWith);
                if (endpoint.method.isHttpMethod() && !pluginOwned) {
                    routed.add(endpoint.method.name() + " " + endpoint.path);
                }
            }

            JsonNode paths = JsonUtils.OBJECT_MAPPER
                    .readTree(client.get("/openapi.json").body().string())
                    .get("paths");
            Set<String> documented = new TreeSet<>();
            paths.properties()
                    .forEach(path -> path.getValue()
                            .properties()
                            .forEach(operation ->
                                    documented.add(operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey())));

            assertFalse(routed.isEmpty(), "no routes found; has the router API changed?");

            List<String> undocumented = new ArrayList<>(routed);
            undocumented.removeAll(documented);
            assertEquals(List.of(), undocumented, "Routes without a matching @OpenApi annotation (method + path)");
        });
    }
}
