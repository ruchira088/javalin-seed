package com.ruchij.web.routes;

import com.ruchij.App;
import com.ruchij.service.health.HealthService;
import com.ruchij.service.health.models.ServiceInformation;
import com.ruchij.web.Routes;
import io.javalin.testtools.JavalinTest;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static com.ruchij.utils.JsonUtils.OBJECT_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceRouteTest {

    @Test
    void shouldReturnServiceInformation() {
        HealthService healthService = Mockito.mock(HealthService.class);
        Instant timestamp = Instant.parse("2023-02-05T04:37:42.566735Z");

        Mockito.when(healthService.serviceInformation())
            .thenReturn(new ServiceInformation(
                "javalin-seed",
                "17.0.5",
                "7.6",
                timestamp,
                "main",
                "my-commit",
                timestamp
            ));

        Routes routes = new Routes(healthService);

        JavalinTest.test(App.javalin(routes, List.of()), ((server, client) -> {
            Response response = client.get("/service/info");
            assertEquals(200, response.code());

            String expectedResponseBody = """
                {
                    "serviceName": "javalin-seed",
                    "javaVersion": "17.0.5",
                    "gradleVersion": "7.6",
                    "currentTimestamp": "2023-02-05T04:37:42.566735Z",
                    "gitBranch": "main",
                    "gitCommit": "my-commit",
                    "buildTimestamp": "2023-02-05T04:37:42.566735Z"
                 }
                """;

            assertEquals(
                OBJECT_MAPPER.readTree(expectedResponseBody),
                OBJECT_MAPPER.readTree(response.body().byteStream())
            );
        }));
    }
}
