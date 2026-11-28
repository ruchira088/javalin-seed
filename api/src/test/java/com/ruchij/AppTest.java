package com.ruchij;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruchij.config.ApplicationConfiguration;
import com.ruchij.config.HttpConfiguration;
import com.ruchij.config.ManagementConfiguration;
import com.ruchij.metrics.MetricsRegistry;
import com.ruchij.service.health.HealthService;
import com.ruchij.service.health.models.ServiceInformation;
import com.ruchij.testing.JavalinTestSupport;
import com.ruchij.utils.JsonUtils;
import com.ruchij.web.Routes;
import io.javalin.Javalin;
import io.javalin.testtools.Response;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AppTest {

    private Routes createMockRoutes() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation())
                .thenReturn(new ServiceInformation(
                        "test-app", "21", "8.0", Instant.now(), "main", "abc123", Instant.now()));
        return new Routes(healthService);
    }

    @Test
    void shouldCreateJavalinAppWithRoutes() {
        Routes routes = createMockRoutes();
        Javalin app = App.javalin(routes, List.of());

        JavalinTestSupport.test(app, (server, client) -> {
            Response response = client.get("/service/info");
            assertEquals(200, response.code());
        });
    }

    @Test
    void shouldConfigureAllowedOrigins() {
        Routes routes = createMockRoutes();
        List<String> customOrigins = List.of("https://custom.example.com");
        Javalin app = App.javalin(routes, customOrigins);

        JavalinTestSupport.test(app, (server, client) -> {
            Response allowed =
                    client.get("/service/info", request -> request.header("Origin", "https://custom.example.com"));
            assertEquals(200, allowed.code());
            assertEquals("https://custom.example.com", header(allowed, "Access-Control-Allow-Origin"));

            Response disallowed =
                    client.get("/service/info", request -> request.header("Origin", "http://localhost:3000"));
            assertNull(disallowed.headers().get("Access-Control-Allow-Origin"));
        });
    }

    @Test
    void shouldOmitCorsCredentialsByDefault() {
        Javalin app = App.javalin(createMockRoutes(), List.of("https://custom.example.com"));

        JavalinTestSupport.test(app, (server, client) -> {
            Response response =
                    client.get("/service/info", request -> request.header("Origin", "https://custom.example.com"));
            assertEquals("https://custom.example.com", header(response, "Access-Control-Allow-Origin"));
            assertNull(response.headers().get("Access-Control-Allow-Credentials"));
        });
    }

    @Test
    void shouldSendCorsCredentialsWhenEnabled() {
        HttpConfiguration httpConfiguration =
                new HttpConfiguration(0, List.of("https://custom.example.com"), true, true);
        Javalin app = App.javalin(createMockRoutes(), httpConfiguration, new SimpleMeterRegistry());

        JavalinTestSupport.test(app, (server, client) -> {
            Response response =
                    client.get("/service/info", request -> request.header("Origin", "https://custom.example.com"));
            assertEquals("true", header(response, "Access-Control-Allow-Credentials"));
        });
    }

    @Test
    void shouldNotAllowAnyOriginWhenNoneAreConfigured() {
        Javalin app = App.javalin(createMockRoutes(), List.of());

        JavalinTestSupport.test(app, (server, client) -> {
            Response response =
                    client.get("/service/info", request -> request.header("Origin", "http://localhost:3000"));
            assertEquals(200, response.code());
            assertNull(response.headers().get("Access-Control-Allow-Origin"));
        });
    }

    @Test
    void shouldUseJacksonJsonMapper() {
        Routes routes = createMockRoutes();
        Javalin app = App.javalin(routes, List.of());

        JavalinTestSupport.test(app, (server, client) -> {
            Response response = client.get("/service/info");
            JsonNode json = JsonUtils.OBJECT_MAPPER.readTree(response.body().string());
            assertNotNull(json.get("serviceName"));
            assertEquals("test-app", json.get("serviceName").asText());
        });
    }

    @Test
    void shouldSerializeInstantsCorrectly() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Instant fixedInstant = Instant.parse("2024-01-15T10:30:00Z");
        Mockito.when(healthService.serviceInformation())
                .thenReturn(
                        new ServiceInformation("test-app", "21", "8.0", fixedInstant, "main", "abc123", fixedInstant));

        Routes routes = new Routes(healthService);
        Javalin app = App.javalin(routes, List.of());

        JavalinTestSupport.test(app, (server, client) -> {
            Response response = client.get("/service/info");
            JsonNode json = JsonUtils.OBJECT_MAPPER.readTree(response.body().string());
            assertEquals("2024-01-15T10:30:00Z", json.get("currentTimestamp").asText());
            assertEquals("2024-01-15T10:30:00Z", json.get("buildTimestamp").asText());
        });
    }

    @Test
    void shouldServeOpenApiSpec() {
        Routes routes = createMockRoutes();
        Javalin app = App.javalin(routes, List.of());

        JavalinTestSupport.test(app, (server, client) -> {
            Response response = client.get("/openapi.json");
            assertEquals(200, response.code());

            JsonNode json = JsonUtils.OBJECT_MAPPER.readTree(response.body().string());
            assertEquals("Javalin Seed API", json.at("/info/title").asText());
            assertNotNull(json.at("/paths/~1service~1info"));
        });
    }

    @Test
    void shouldNotServeApiDocsWhenDisabled() {
        HttpConfiguration httpConfiguration = new HttpConfiguration(0, List.of(), false, false);
        Javalin app = App.javalin(createMockRoutes(), httpConfiguration, new SimpleMeterRegistry());

        JavalinTestSupport.test(app, (server, client) -> {
            assertEquals(404, client.get("/openapi.json").code());
            assertEquals(404, client.get("/swagger").code());
            assertEquals(200, client.get("/service/info").code());
        });
    }

    @Test
    void shouldServeSwaggerUi() {
        Routes routes = createMockRoutes();
        Javalin app = App.javalin(routes, List.of());

        JavalinTestSupport.test(app, (server, client) -> {
            Response response = client.get("/swagger");
            assertEquals(200, response.code());
        });
    }

    @Test
    void shouldStartAndStopServerWithRunMethod() throws Exception {
        int port = findAvailablePort();
        int managementPort = findAvailablePort();
        ApplicationConfiguration config = new ApplicationConfiguration(
                new HttpConfiguration(port, List.of(), false, true), new ManagementConfiguration(managementPort));

        Thread serverThread = new Thread(() -> App.run(config));
        serverThread.start();

        String url = "http://127.0.0.1:" + port + "/service/info";
        HttpResponse<String> response = waitForServer(url);
        assertEquals(200, response.statusCode());
        JsonNode json = JsonUtils.OBJECT_MAPPER.readTree(response.body());
        assertNotNull(json.get("serviceName"));
        assertEquals("javalin-seed", json.get("serviceName").asText());

        HttpResponse<String> metrics = waitForServer("http://127.0.0.1:" + managementPort + "/metrics");
        assertEquals(200, metrics.statusCode());
        assertTrue(metrics.body().contains("http_server_requests_seconds_count{"), metrics.body());
        assertTrue(metrics.body().contains("uri=\"/service/info\""), metrics.body());
        assertTrue(metrics.body().contains("jvm_memory_used_bytes{"), metrics.body());

        HttpResponse<String> publicMetrics = waitForServer("http://127.0.0.1:" + port + "/metrics");
        assertEquals(404, publicMetrics.statusCode());
    }

    @Test
    void shouldServePrometheusMetricsFromManagementApp() {
        PrometheusMeterRegistry registry = MetricsRegistry.create("test-app");

        JavalinTestSupport.test(App.managementJavalin(registry), (server, client) -> {
            Response response = client.get("/metrics");
            assertEquals(200, response.code());
            assertEquals(App.PROMETHEUS_CONTENT_TYPE, header(response, "Content-Type"));
            assertTrue(response.body().string().contains("application=\"test-app\""));
        });
    }

    @Test
    void shouldStartServerWithMainMethod() throws Exception {
        Thread mainThread = new Thread(() -> App.main(new String[] {}));
        mainThread.setDaemon(true);
        mainThread.start();

        String url = "http://127.0.0.1:19999/service/info";
        HttpResponse<String> response = waitForServer(url);
        assertEquals(200, response.statusCode());
        JsonNode json = JsonUtils.OBJECT_MAPPER.readTree(response.body());
        assertEquals("javalin-seed", json.get("serviceName").asText());
    }

    private static HttpResponse<String> waitForServer(String url) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request =
                    HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

            int maxAttempts = 20;
            for (int i = 0; i < maxAttempts; i++) {
                try {
                    return client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException e) {
                    Thread.sleep(500);
                }
            }

            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static int findAvailablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String header(Response response, String name) {
        List<String> values = response.headers().get(name);
        assertNotNull(values, "missing " + name + " header");
        return values.getFirst();
    }
}
