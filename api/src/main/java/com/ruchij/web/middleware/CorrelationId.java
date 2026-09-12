package com.ruchij.web.middleware;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Propagates a correlation ID through each request: it is read from the {@value #HEADER_NAME} request header
 * (or generated when absent), exposed to log statements via the {@value #MDC_KEY} MDC key, and echoed back on the
 * response so callers can quote it when reporting problems.
 *
 * <p>Because the value is written into log output, a supplied header is only honoured when it matches
 * {@link #VALID_CORRELATION_ID}; anything else (control characters, overlong values) is replaced with a generated ID.
 */
public final class CorrelationId {
    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private CorrelationId() {
    }

    public static void register(RoutesConfig routes) {
        routes.before(CorrelationId::start);
        routes.after(CorrelationId::clear);
    }

    static void start(Context context) {
        String header = context.header(HEADER_NAME);
        String correlationId =
            header != null && VALID_CORRELATION_ID.matcher(header).matches() ? header : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, correlationId);
        context.attribute(MDC_KEY, correlationId);
        context.header(HEADER_NAME, correlationId);
    }

    static void clear(Context context) {
        MDC.remove(MDC_KEY);
    }
}
