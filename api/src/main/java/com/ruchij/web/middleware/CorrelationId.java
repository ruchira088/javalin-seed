package com.ruchij.web.middleware;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Propagates a correlation ID through each request: it is read from the {@value #HEADER_NAME} request header,
 * falling back to the trace ID of a W3C {@value #TRACEPARENT_HEADER} header (so a request arriving from a traced
 * service keeps the same ID in these logs), and is generated when neither is usable. It is exposed to log statements
 * via the {@value #MDC_KEY} MDC key, and echoed back on the response so callers can quote it when reporting problems.
 *
 * <p>Because the value is written into log output, a supplied header is only honoured when it matches
 * {@link #VALID_CORRELATION_ID} (or, for {@value #TRACEPARENT_HEADER}, is a well-formed W3C value); anything else
 * (control characters, overlong values) is replaced with a generated ID.
 */
public final class CorrelationId {
    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String MDC_KEY = "correlationId";

    static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    // W3C Trace Context: version-traceid-parentid-flags, lower-case hex. Version ff is forbidden, and later versions
    // may append fields, which are allowed only when the version is not 00.
    private static final Pattern TRACEPARENT =
        Pattern.compile("(?<version>[0-9a-f]{2})-(?<traceId>[0-9a-f]{32})-(?<parentId>[0-9a-f]{16})-[0-9a-f]{2}(?<rest>-.*)?");
    private static final String INVALID_TRACE_ID = "0".repeat(32);
    private static final String INVALID_PARENT_ID = "0".repeat(16);

    private CorrelationId() {
    }

    public static void register(RoutesConfig routes) {
        routes.before(CorrelationId::start);
        routes.after(CorrelationId::clear);
    }

    static void start(Context context) {
        String header = context.header(HEADER_NAME);
        String correlationId = header != null && VALID_CORRELATION_ID.matcher(header).matches()
            ? header
            : traceId(context.header(TRACEPARENT_HEADER)).orElseGet(() -> UUID.randomUUID().toString());

        MDC.put(MDC_KEY, correlationId);
        context.attribute(MDC_KEY, correlationId);
        context.header(HEADER_NAME, correlationId);
    }

    static Optional<String> traceId(String traceparent) {
        if (traceparent == null) {
            return Optional.empty();
        }

        Matcher matcher = TRACEPARENT.matcher(traceparent.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String version = matcher.group("version");
        boolean validVersion = !version.equals("ff") && (matcher.group("rest") == null || !version.equals("00"));
        String traceId = matcher.group("traceId");
        boolean validIds = !traceId.equals(INVALID_TRACE_ID) && !matcher.group("parentId").equals(INVALID_PARENT_ID);

        return validVersion && validIds ? Optional.of(traceId) : Optional.empty();
    }

    static void clear(Context context) {
        MDC.remove(MDC_KEY);
    }
}
