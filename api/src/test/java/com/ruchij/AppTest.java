package com.ruchij;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruchij.config.ApplicationConfiguration;
import com.ruchij.config.HttpConfiguration;
import com.ruchij.service.health.HealthService;
import com.ruchij.service.health.models.ServiceInformation;
import com.ruchij.utils.JsonUtils;
import com.ruchij.web.Routes;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppTest {

    private Routes createMockRoutes() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation())
            .thenReturn(new ServiceInformation(
                "test-app", "21", "8.0", Instant.now(), "main", "abc123", Instant.now()
            ));
        return new Routes(healthService);
    }

    @Test
    void shouldCreateJavalinAppWithRoutes() {
        Routes routes = createMockRoutes();
        Javalin app = App.javalin(routes, List.of());

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/service/info");
            assertEquals(200, response.code());
        });
    }

    @Test
    void shouldConfigureAllowedOrigins() {
        Routes routes = createMockRoutes();
        List<String> customOrigins = List.of("https://custom.example.com");
        Javalin app = App.javalin(routes, customOrigins);

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/service/info");
            assertEquals(200, response.code());
        });
    }

    @Test
    void shouldUseJacksonJsonMapper() throws Exception {
        Routes routes = createMockRoutes();
        Javalin app = App.javalin(routes, List.of());

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/service/info");
            JsonNode json = JsonUtils.OBJECT_MAPPER.readTree(response.body().byteStream());
            assertNotNull(json.get("serviceName"));
            assertEquals("test-app", json.get("serviceName").asText());
        });
    }

    @Test
    void shouldSerializeInstantsCorrectly() throws Exception {
        HealthService healthService = Mockito.mock(HealthService.class);
        Instant fixedInstant = Instant.parse("2024-01-15T10:30:00Z");
        Mockito.when(healthService.serviceInformation())
            .thenReturn(new ServiceInformation(
                "test-app", "21", "8.0", fixedInstant, "main", "abc123", fixedInstant
            ));

        Routes routes = new Routes(healthService);
        Javalin app = App.javalin(routes, List.of());

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/service/info");
            JsonNode json = JsonUtils.OBJECT_MAPPER.readTree(response.body().byteStream());
            assertEquals("2024-01-15T10:30:00Z", json.get("currentTimestamp").asText());
            assertEquals("2024-01-15T10:30:00Z", json.get("buildTimestamp").asText());
        });
    }

    @Test
    void shouldServeOpenApiSpec() {
        Routes routes = createMockRoutes();
        Javalin app = App.javalin(routes, List.of());

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/openapi.json");
            assertEquals(200, response.code());

            JsonNode json = JsonUtils.OBJECT_MAPPER.readTree(response.body().byteStream());
            assertEquals("Javalin Seed API", json.at("/info/title").asText());
            assertNotNull(json.at("/paths/~1service~1info"));
        });
    }

    @Test
    void shouldServeSwaggerUi() {
        Routes routes = createMockRoutes();
        Javalin app = App.javalin(routes, List.of());

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/swagger");
            assertEquals(200, response.code());
        });
    }

    @Test
    void shouldStartAndStopServerWithRunMethod() throws Exception {
        int port = findAvailablePort();
        ApplicationConfiguration config = new ApplicationConfiguration(
            new HttpConfiguration(port, List.of())
        );

        Thread serverThread = new Thread(() -> App.run(config));
        serverThread.start();

        String url = "http://127.0.0.1:" + port + "/service/info";
        try (Response response = waitForServer(url)) {
            assertEquals(200, response.code());
            JsonNode json = JsonUtils.OBJECT_MAPPER.readTree(
                Objects.requireNonNull(response.body()).byteStream()
            );
            assertNotNull(json.get("serviceName"));
            assertEquals("javalin-seed", json.get("serviceName").asText());
        }
    }

    @Test
    void shouldStartServerWithMainMethod() throws Exception {
        Thread mainThread = new Thread(() -> App.main(new String[]{}));
        mainThread.setDaemon(true);
        mainThread.start();

        String url = "http://127.0.0.1:19999/service/info";
        try (Response response = waitForServer(url)) {
            assertEquals(200, response.code());
            JsonNode json = JsonUtils.OBJECT_MAPPER.readTree(
                Objects.requireNonNull(response.body()).byteStream()
            );
            assertEquals("javalin-seed", json.get("serviceName").asText());
        }
    }

    private static Response waitForServer(String url) throws Exception {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).build();

        int maxAttempts = 20;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                return client.newCall(request).execute();
            } catch (IOException e) {
                Thread.sleep(500);
            }
        }

        return client.newCall(request).execute();
    }

    private static int findAvailablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
