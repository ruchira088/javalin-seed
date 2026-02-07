package com.ruchij.web.middleware;

import com.ruchij.App;
import com.ruchij.exceptions.ResourceConflictException;
import com.ruchij.exceptions.ResourceNotFoundException;
import com.ruchij.exceptions.ValidationException;
import com.ruchij.service.health.HealthService;
import com.ruchij.service.health.models.ServiceInformation;
import com.ruchij.utils.JsonUtils;
import com.ruchij.web.Routes;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExceptionMapperTest {

    private Javalin createApp() {
        Javalin app = Javalin.create(config -> config.jsonMapper(new JavalinJackson(JsonUtils.OBJECT_MAPPER, true)));
        ExceptionMapper.handle(app);
        return app;
    }

    @Test
    void shouldReturn400ForValidationException() {
        Javalin app = createApp();
        app.get("/test", ctx -> {
            throw new ValidationException("Invalid input provided");
        });

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/test");
            assertEquals(400, response.code());

            String expectedBody = """
                {"error":"Invalid input provided"}
                """;
            assertEquals(
                JsonUtils.OBJECT_MAPPER.readTree(expectedBody),
                JsonUtils.OBJECT_MAPPER.readTree(response.body().byteStream())
            );
        });
    }

    @Test
    void shouldReturn404ForResourceNotFoundException() {
        Javalin app = createApp();
        app.get("/test", ctx -> {
            throw new ResourceNotFoundException("User not found");
        });

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/test");
            assertEquals(404, response.code());

            String expectedBody = """
                {"error":"User not found"}
                """;
            assertEquals(
                JsonUtils.OBJECT_MAPPER.readTree(expectedBody),
                JsonUtils.OBJECT_MAPPER.readTree(response.body().byteStream())
            );
        });
    }

    @Test
    void shouldReturn409ForResourceConflictException() {
        Javalin app = createApp();
        app.get("/test", ctx -> {
            throw new ResourceConflictException("Resource already exists");
        });

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/test");
            assertEquals(409, response.code());

            String expectedBody = """
                {"error":"Resource already exists"}
                """;
            assertEquals(
                JsonUtils.OBJECT_MAPPER.readTree(expectedBody),
                JsonUtils.OBJECT_MAPPER.readTree(response.body().byteStream())
            );
        });
    }

    @Test
    void shouldReturn500ForGenericException() {
        Javalin app = createApp();
        app.get("/test", ctx -> {
            throw new RuntimeException("Unexpected error");
        });

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/test");
            assertEquals(500, response.code());

            String expectedBody = """
                {"error":"Unexpected error"}
                """;
            assertEquals(
                JsonUtils.OBJECT_MAPPER.readTree(expectedBody),
                JsonUtils.OBJECT_MAPPER.readTree(response.body().byteStream())
            );
        });
    }

    @Test
    void shouldReturn404ForNonMatchingRoute() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Mockito.when(healthService.serviceInformation())
            .thenReturn(new ServiceInformation(
                "test", "21", "8.0", Instant.now(), "main", "abc123", Instant.now()
            ));

        Routes routes = new Routes(healthService);
        Javalin app = App.javalin(routes, List.of());
        ExceptionMapper.handle(app);

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/non-existing-route");
            assertEquals(404, response.code());

            String expectedBody = """
                {"error":"No matching routes were found for GET /non-existing-route"}
                """;
            assertEquals(
                JsonUtils.OBJECT_MAPPER.readTree(expectedBody),
                JsonUtils.OBJECT_MAPPER.readTree(response.body().byteStream())
            );
        });
    }

    @Test
    void shouldStoreExceptionAsContextAttribute() {
        Javalin app = createApp();
        ValidationException testException = new ValidationException("Test error");
        app.get("/test", ctx -> {
            throw testException;
        });
        app.after(ctx -> {
            Exception stored = ctx.attribute("exception");
            if (stored != null) {
                ctx.header("X-Exception-Stored", "true");
            }
        });

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/test");
            assertEquals(400, response.code());
            assertEquals("true", response.header("X-Exception-Stored"));
        });
    }

    @Test
    void shouldNotOverrideExplicit404FromRoute() {
        Javalin app = createApp();
        app.get("/explicit-404", ctx -> {
            ctx.status(HttpStatus.NOT_FOUND).result("Custom not found");
        });

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/explicit-404");
            assertEquals(404, response.code());
            assertEquals("Custom not found", response.body().string());
        });
    }
}
