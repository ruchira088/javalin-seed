package com.ruchij.web.routes;

import com.ruchij.service.health.HealthService;
import com.ruchij.service.health.models.ServiceInformation;
import io.javalin.apibuilder.EndpointGroup;
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
    path("/info", () ->
        get(context -> {
              ServiceInformation serviceInformation = this.healthService.serviceInformation();
              context.status(HttpStatus.OK).json(serviceInformation);
            }
        )
    );
  }
}
