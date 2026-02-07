package com.ruchij.web.routes;

import com.ruchij.service.health.HealthService;
import com.ruchij.service.health.models.ServiceInformation;
import com.ruchij.web.responses.ErrorResponse;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class ServiceRoute implements EndpointGroup {
    private final HealthService healthService;

    public ServiceRoute(HealthService healthService) {
        this.healthService = healthService;
    }

    @Override
    public void addEndpoints() {
        path("/info", () -> get(this::getServiceInfo));
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
                content = @OpenApiContent(from = ServiceInformation.class)
            ),
            @OpenApiResponse(
                status = "500",
                description = "Internal server error",
                content = @OpenApiContent(from = ErrorResponse.class)
            )
        }
    )
    private void getServiceInfo(Context context) {
        ServiceInformation serviceInformation = this.healthService.serviceInformation();
        context.status(HttpStatus.OK).json(serviceInformation);
    }
}
