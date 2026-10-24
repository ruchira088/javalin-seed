package com.ruchij.web.middleware;

import io.javalin.config.RequestLoggerConfig;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

/**
 * Writes one access-log line per HTTP request: method, path, status and duration.
 *
 * <p>Javalin invokes the request logger after the {@code after} handlers, by which point {@link CorrelationId} has
 * already removed the correlation ID from the MDC, so it is restored here from the {@link Context} attribute for the
 * duration of the log call. Kubernetes probes are logged at DEBUG so they do not drown out real traffic.
 */
public final class RequestLogging {
    static final String PROBE_USER_AGENT_PREFIX = "kube-probe/";

    private static final Logger logger = LoggerFactory.getLogger(RequestLogging.class);

    private RequestLogging() {
    }

    public static void register(RequestLoggerConfig requestLoggerConfig) {
        requestLoggerConfig.http(RequestLogging::log);
    }

    static void log(Context context, Float executionTimeMs) {
        String userAgent = context.userAgent();
        Level level = userAgent != null && userAgent.startsWith(PROBE_USER_AGENT_PREFIX) ? Level.DEBUG : Level.INFO;

        try (var _ = MDC.putCloseable(CorrelationId.MDC_KEY, context.attribute(CorrelationId.MDC_KEY))) {
            logger.atLevel(level).log(
                "{} {} {} {}ms",
                context.method(),
                context.path(),
                context.statusCode(),
                "%.1f".formatted(executionTimeMs)
            );
        }
    }
}
