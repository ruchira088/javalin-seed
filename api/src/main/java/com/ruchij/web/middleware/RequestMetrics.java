package com.ruchij.web.middleware;

import io.javalin.http.Context;
import io.javalin.router.Endpoint;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.http.Outcome;
import java.util.concurrent.TimeUnit;

/**
 * Records an {@value #METRIC_NAME} timer per request, tagged with method, route, status and outcome, and publishing a
 * latency histogram so percentiles can be aggregated across pods.
 *
 * <p>The {@code uri} tag is the matched route <em>template</em> (e.g. {@code /users/{id}}), never the raw path:
 * raw paths would create a new time series per distinct ID. Requests no route matched share {@value #NOT_FOUND_URI}.
 */
public final class RequestMetrics {
    public static final String METRIC_NAME = "http.server.requests";
    static final String NOT_FOUND_URI = "NOT_FOUND";

    private final MeterRegistry meterRegistry;

    public RequestMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(Context context, Float executionTimeMs) {
        Endpoint endpoint = context.endpoints().lastHttpEndpoint();
        int status = context.statusCode();

        Timer.builder(METRIC_NAME)
                .tag("method", context.method().name())
                .tag("uri", endpoint == null ? NOT_FOUND_URI : endpoint.path)
                .tag("status", Integer.toString(status))
                .tag("outcome", Outcome.forStatus(status).name())
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record((long) (executionTimeMs * 1_000_000), TimeUnit.NANOSECONDS);
    }
}
