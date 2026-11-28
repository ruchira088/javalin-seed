package com.ruchij.web.middleware;

import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import java.util.Map;

/**
 * Adds baseline security headers to every response, including errors and 404s (they are set in a {@code before}
 * handler, which runs ahead of route matching and whose headers survive exception mapping).
 *
 * <p>HSTS is sent unconditionally: browsers ignore it over plain HTTP, and every deployed host is served over TLS by the
 * Ingress. A Content-Security-Policy is deliberately not set here because Swagger UI relies on inline scripts; the JSON
 * API is already protected by {@code nosniff}.
 */
public final class SecurityHeaders {
    static final Map<String, String> HEADERS = Map.of(
            "X-Content-Type-Options", "nosniff",
            "X-Frame-Options", "DENY",
            "Referrer-Policy", "no-referrer",
            "Strict-Transport-Security", "max-age=31536000");

    private SecurityHeaders() {}

    public static void register(RoutesConfig routes) {
        routes.before(SecurityHeaders::apply);
    }

    static void apply(Context context) {
        HEADERS.forEach(context::header);
    }
}
