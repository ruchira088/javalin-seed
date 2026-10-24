package com.ruchij.web.responses;

public record HealthCheckResponse(String status) {
    public static final HealthCheckResponse OK = new HealthCheckResponse("ok");
}
