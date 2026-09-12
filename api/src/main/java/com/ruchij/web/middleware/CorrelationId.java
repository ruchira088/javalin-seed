package com.ruchij.web.middleware;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Propagates a correlation ID through each request: it is read from the {@value #HEADER_NAME} request header
 * (or generated when absent), exposed to log statements via the {@value #MDC_KEY} MDC key, and echoed back on the
 * response so callers can quote it when reporting problems.
 */
public final class CorrelationId {
    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static void register(RoutesConfig routes) {
        routes.before(CorrelationId::start);
        routes.after(CorrelationId::clear);
    }

    static void start(Context context) {
        String header = context.header(HEADER_NAME);
        String correlationId = header == null || header.isBlank() ? UUID.randomUUID().toString() : header;

        MDC.put(MDC_KEY, correlationId);
        context.attribute(MDC_KEY, correlationId);
        context.header(HEADER_NAME, correlationId);
    }

    static void clear(Context context) {
        MDC.remove(MDC_KEY);
    }
}
