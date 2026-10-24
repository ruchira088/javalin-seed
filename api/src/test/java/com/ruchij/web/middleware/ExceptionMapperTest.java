package com.ruchij.web.middleware;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExceptionMapperTest {

    private Javalin createAppWithRoute(io.javalin.http.Handler handler) {
        return Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(JsonUtils.OBJECT_MAPPER, true));
            config.routes.get("/test", handler);
            ExceptionMapper.handle(config.routes);
        });
    }

    @Test
    void shouldReturn400ForValidationException() {
        Javalin app = createAppWithRoute(ctx -> {
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
                JsonUtils.OBJECT_MAPPER.readTree(response.body().string())
            );
        });
    }

    @Test
    void shouldReturn404ForResourceNotFoundException() {
        Javalin app = createAppWithRoute(ctx -> {
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
                JsonUtils.OBJECT_MAPPER.readTree(response.body().string())
            );
        });
    }

    @Test
    void shouldReturn409ForResourceConflictException() {
        Javalin app = createAppWithRoute(ctx -> {
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
                JsonUtils.OBJECT_MAPPER.readTree(response.body().string())
            );
        });
    }

    @Test
    void shouldReturnGeneric500WithoutLeakingExceptionMessage() {
        Javalin app = createAppWithRoute(ctx -> {
            throw new RuntimeException("Connection refused: db.internal:5432");
        });

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/test");
            assertEquals(500, response.code());

            String expectedBody = """
                {"error":"An unexpected error occurred"}
                """;
            assertEquals(
                JsonUtils.OBJECT_MAPPER.readTree(expectedBody),
                JsonUtils.OBJECT_MAPPER.readTree(response.body().string())
            );
        });
    }

    @Test
    void shouldLogUnhandledExceptionWithStackTrace() {
        RuntimeException exception = new RuntimeException("Connection refused: db.internal:5432");
        Javalin app = createAppWithRoute(ctx -> {
            throw exception;
        });

        Logger logger = (Logger) LoggerFactory.getLogger(ExceptionMapper.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            JavalinTest.test(app, (server, client) -> {
                client.get("/test");

                assertEquals(1, appender.list.size());
                ILoggingEvent event = appender.list.getFirst();
                assertEquals(Level.ERROR, event.getLevel());
                assertEquals("Unhandled exception for GET /test", event.getFormattedMessage());
                assertEquals(exception.getMessage(), event.getThrowableProxy().getMessage());
            });
        } finally {
            logger.detachAppender(appender);
        }
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

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/non-existing-route");
            assertEquals(404, response.code());

            String expectedBody = """
                {"error":"No matching routes were found for GET /non-existing-route"}
                """;
            assertEquals(
                JsonUtils.OBJECT_MAPPER.readTree(expectedBody),
                JsonUtils.OBJECT_MAPPER.readTree(response.body().string())
            );
        });
    }

    @Test
    void shouldStoreExceptionAsContextAttribute() {
        ValidationException testException = new ValidationException("Test error");
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(JsonUtils.OBJECT_MAPPER, true));
            config.routes.get("/test", ctx -> {
                throw testException;
            });
            config.routes.after(ctx -> {
                Exception stored = ctx.attribute("exception");
                if (stored != null) {
                    ctx.header("X-Exception-Stored", "true");
                }
            });
            ExceptionMapper.handle(config.routes);
        });

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/test");
            assertEquals(400, response.code());
            assertEquals("true", response.headers().get("X-Exception-Stored").getFirst());
        });
    }

    @Test
    void shouldNotOverrideExplicit404FromRoute() {
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(JsonUtils.OBJECT_MAPPER, true));
            config.routes.get("/explicit-404", ctx -> {
                ctx.status(HttpStatus.NOT_FOUND).result("Custom not found");
            });
            ExceptionMapper.handle(config.routes);
        });

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/explicit-404");
            assertEquals(404, response.code());
            assertEquals("Custom not found", response.body().string());
        });
    }
}
