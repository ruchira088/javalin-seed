package com.ruchij.web.responses;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * An RFC 9457 "problem details" error body, served as {@value #CONTENT_TYPE}.
 *
 * <p>{@code type} is always {@value #DEFAULT_TYPE}, which per the RFC means {@code title} is simply the HTTP status
 * phrase. {@code detail} carries the (caller-safe) explanation. {@code correlationId} and {@code errors} are extension
 * members: the former matches the {@code X-Correlation-ID} response header and the server logs, the latter lists
 * per-field validation failures. Null members are omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    String correlationId,
    Map<String, List<String>> errors
) {
    public static final String CONTENT_TYPE = "application/problem+json";
    public static final String DEFAULT_TYPE = "about:blank";

    public ProblemDetail {
        errors = errors == null ? null : Map.copyOf(errors);
    }
}
