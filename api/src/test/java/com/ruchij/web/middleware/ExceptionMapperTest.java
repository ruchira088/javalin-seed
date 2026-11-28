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
import com.ruchij.web.json.RequestBodyJsonMapper;
import com.ruchij.web.responses.ProblemDetail;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

            assertProblem(response, "Invalid input provided");
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

            assertProblem(response, "User not found");
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

            assertProblem(response, "Resource already exists");
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

            assertProblem(response, "An unexpected error occurred");
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

            assertProblem(response, "No matching routes were found for GET /non-existing-route");
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

    @Test
    void shouldReturn404ForUnregisteredMethodOnExistingPath() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Javalin app = App.javalin(new Routes(healthService), List.of());

        JavalinTest.test(app, (server, client) -> {
            Response response = client.post("/service/health", "");
            assertEquals(404, response.code());

            assertProblem(response, "No matching routes were found for POST /service/health");
        });
    }

    @Test
    void shouldReturn400ForMalformedJsonBody() {
        assertBadRequestForBody("{not json");
    }

    @Test
    void shouldReturn400ForBodyWithWrongFieldType() {
        assertBadRequestForBody("""
            {"serviceName":"svc","buildTimestamp":"not-a-timestamp"}
            """);
    }

    private void assertBadRequestForBody(String body) {
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new RequestBodyJsonMapper(JsonUtils.OBJECT_MAPPER));
            config.routes.post("/test", ctx -> ctx.json(ctx.bodyAsClass(ServiceInformation.class)));
            ExceptionMapper.handle(config.routes);
        });

        JavalinTest.test(app, (server, client) -> {
            Response response = client.post("/test", body);
            assertEquals(400, response.code());

            assertProblem(response, "The request body could not be parsed");
        });
    }

    @Test
    void shouldReturn404ForUnmatchedRouteWithoutAnyBeforeHandlers() {
        Javalin app = createAppWithRoute(ctx -> ctx.result("ok"));

        JavalinTest.test(app, (server, client) -> {
            Response response = client.post("/test", "");
            assertEquals(404, response.code());

            assertProblem(response, "No matching routes were found for POST /test");
        });
    }

    @Test
    void shouldReturnProblemDetailWithInstanceAndCorrelationId() {
        Javalin app = App.javalin(new Routes(Mockito.mock(HealthService.class)), List.of());

        JavalinTest.test(app, (server, client) -> {
            Response response =
                client.get("/missing", request -> request.header(CorrelationId.HEADER_NAME, "problem-test"));

            assertContentType(response);
            JsonNode body = JsonUtils.OBJECT_MAPPER.readTree(response.body().string());
            assertEquals("about:blank", body.get("type").asText());
            assertEquals("Not Found", body.get("title").asText());
            assertEquals(404, body.get("status").asInt());
            assertEquals("/missing", body.get("instance").asText());
            assertEquals("problem-test", body.get("correlationId").asText());
            assertFalse(body.has("errors"));
        });
    }

    @Test
    void shouldMapJavalinHttpResponseExceptionsToProblemDetail() {
        Javalin app = createAppWithRoute(ctx -> {
            throw new BadRequestResponse("Missing tenant");
        });

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/test");
            assertEquals(400, response.code());
            assertProblem(response, "Missing tenant");
        });
    }

    @Test
    void shouldMapJavalinValidatorFailuresToProblemDetailWithFieldErrors() {
        Javalin app = createAppWithRoute(ctx -> ctx.result(String.valueOf(ctx.queryParamAsClass("limit", Integer.class).get())));

        JavalinTest.test(app, (server, client) -> {
            Response response = client.get("/test?limit=abc");
            assertEquals(400, response.code());

            JsonNode body = JsonUtils.OBJECT_MAPPER.readTree(response.body().string());
            assertEquals("The request contains invalid parameters", body.get("detail").asText());
            assertEquals("TYPE_CONVERSION_FAILED", body.at("/errors/limit/0").asText());
            assertFalse(body.toString().contains("abc"), "rejected values must not be echoed");
        });
    }

    // Jetty appends "; charset=utf-8", so compare only the media type.
    private static void assertContentType(Response response) {
        List<String> contentTypes = response.headers().get("Content-Type");
        assertNotNull(contentTypes, "missing Content-Type");
        String contentType = contentTypes.getFirst();
        assertEquals(ProblemDetail.CONTENT_TYPE, contentType.split(";")[0].trim(), contentType);
    }

    private static void assertProblem(Response response, String expectedDetail) throws java.io.IOException {
        assertContentType(response);

        JsonNode body = JsonUtils.OBJECT_MAPPER.readTree(response.body().string());
        assertEquals(ProblemDetail.DEFAULT_TYPE, body.get("type").asText());
        assertEquals(HttpStatus.forStatus(response.code()).getMessage(), body.get("title").asText());
        assertEquals(response.code(), body.get("status").asInt());
        assertEquals(expectedDetail, body.get("detail").asText());
    }
}
