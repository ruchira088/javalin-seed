package com.ruchij.web.routes;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

import com.ruchij.service.health.HealthService;
import com.ruchij.service.health.models.ServiceInformation;
import com.ruchij.web.responses.HealthCheckResponse;
import com.ruchij.web.responses.ProblemDetail;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;

public class ServiceRoute implements EndpointGroup {
    private final HealthService healthService;

    public ServiceRoute(HealthService healthService) {
        this.healthService = healthService;
    }

    @Override
    public void addEndpoints() {
        path("/info", () -> get(this::getServiceInfo));
        path("/health", () -> get(this::getHealth));
    }

    @OpenApi(
            path = "/service/health",
            methods = {HttpMethod.GET},
            summary = "Lightweight health check used by the Kubernetes probes",
            tags = {"Service"},
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "The service is up",
                        content = @OpenApiContent(from = HealthCheckResponse.class))
            })
    private void getHealth(Context context) {
        context.status(HttpStatus.OK).json(HealthCheckResponse.OK);
    }

    @OpenApi(
            path = "/service/info",
            methods = {HttpMethod.GET},
            summary = "Get service information",
            tags = {"Service"},
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "Service information",
                        content = @OpenApiContent(from = ServiceInformation.class)),
                @OpenApiResponse(
                        status = "500",
                        description = "Internal server error",
                        content = @OpenApiContent(from = ProblemDetail.class, type = ProblemDetail.CONTENT_TYPE))
            })
    private void getServiceInfo(Context context) {
        ServiceInformation serviceInformation = this.healthService.serviceInformation();
        context.status(HttpStatus.OK).json(serviceInformation);
    }
}
