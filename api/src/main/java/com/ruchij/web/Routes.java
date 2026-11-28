package com.ruchij.web;

import static io.javalin.apibuilder.ApiBuilder.path;

import com.ruchij.service.health.HealthService;
import com.ruchij.web.routes.ServiceRoute;
import io.javalin.apibuilder.EndpointGroup;

public class Routes implements EndpointGroup {
    private final ServiceRoute serviceRoute;

    public Routes(HealthService healthService) {
        this(new ServiceRoute(healthService));
    }

    public Routes(ServiceRoute serviceRoute) {
        this.serviceRoute = serviceRoute;
    }

    @Override
    public void addEndpoints() {
        path("service", serviceRoute);
    }
}
