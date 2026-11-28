package com.ruchij.web.middleware;

import com.ruchij.exceptions.InvalidRequestBodyException;
import com.ruchij.exceptions.ResourceConflictException;
import com.ruchij.exceptions.ResourceNotFoundException;
import com.ruchij.exceptions.ValidationException;
import com.ruchij.web.responses.ProblemDetail;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.ExceptionHandler;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.validation.ValidationError;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps exceptions (and unmatched routes) to RFC 9457 {@link ProblemDetail} bodies, so every error the API returns has
 * the same {@value ProblemDetail#CONTENT_TYPE} shape, including Javalin's own {@link HttpResponseException}s and
 * validator failures, which would otherwise use Javalin's default formats.
 */
public class ExceptionMapper {
    static final String INTERNAL_SERVER_ERROR_MESSAGE = "An unexpected error occurred";

    private static final Logger logger = LoggerFactory.getLogger(ExceptionMapper.class);

    private static <E extends Exception> ExceptionHandler<E> handle(HttpStatus httpStatus) {
        return (exception, context) -> {
            context.attribute("exception", exception);
            respond(context, httpStatus, exception.getMessage(), null);
        };
    }

    // Unexpected failures are logged in full but never echoed to the caller, whose only handle on them is the
    // correlation ID (in the body and the X-Correlation-ID header) that matches the logged line.
    private static ExceptionHandler<Exception> handleUnexpected() {
        return (exception, context) -> {
            logger.error("Unhandled exception for {} {}", context.method(), context.path(), exception);
            context.attribute("exception", exception);
            respond(context, HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR_MESSAGE, null);
        };
    }

    // Raised by Javalin itself or by handlers (e.g. `throw new BadRequestResponse("...")`); the message is
    // developer-chosen, so it is safe to return.
    private static ExceptionHandler<HttpResponseException> handleHttpResponse() {
        return (exception, context) -> {
            context.attribute("exception", exception);
            respond(context, HttpStatus.forStatus(exception.getStatus()), exception.getMessage(), null);
        };
    }

    // Raised by Javalin's validators (ctx.queryParamAsClass(...).get() and friends). Only the error keys are returned,
    // never the rejected values.
    private static ExceptionHandler<io.javalin.validation.ValidationException> handleJavalinValidation() {
        return (exception, context) -> {
            context.attribute("exception", exception);

            Map<String, List<String>> errors = exception.getErrors().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().stream()
                                    .map(ValidationError::getMessage)
                                    .toList()));

            respond(context, HttpStatus.BAD_REQUEST, "The request contains invalid parameters", errors);
        };
    }

    static void respond(
            Context context,
            HttpStatus httpStatus,
            @Nullable String detail,
            @Nullable Map<String, List<String>> errors) {
        ProblemDetail problemDetail = new ProblemDetail(
                ProblemDetail.DEFAULT_TYPE,
                httpStatus.getMessage(),
                httpStatus.getCode(),
                detail,
                context.path(),
                context.attribute(CorrelationId.MDC_KEY),
                errors);

        context.status(httpStatus).json(problemDetail).contentType(ProblemDetail.CONTENT_TYPE);
    }

    public static void handle(RoutesConfig routes) {
        routes.exception(ValidationException.class, ExceptionMapper.handle(HttpStatus.BAD_REQUEST));

        routes.exception(InvalidRequestBodyException.class, ExceptionMapper.handle(HttpStatus.BAD_REQUEST));

        routes.exception(ResourceNotFoundException.class, ExceptionMapper.handle(HttpStatus.NOT_FOUND));

        routes.exception(ResourceConflictException.class, ExceptionMapper.handle(HttpStatus.CONFLICT));

        routes.exception(HttpResponseException.class, ExceptionMapper.handleHttpResponse());

        routes.exception(io.javalin.validation.ValidationException.class, ExceptionMapper.handleJavalinValidation());

        routes.exception(Exception.class, ExceptionMapper.handleUnexpected());

        // Only answer for requests no GET/POST/... handler matched; a handler that sets 404 itself keeps its own body.
        // (endpoint() is not usable here: it throws when no endpoint at all, not even a before handler, matched.)
        routes.error(HttpStatus.NOT_FOUND, context -> {
            if (context.endpoints().lastHttpEndpoint() == null) {
                respond(
                        context,
                        HttpStatus.NOT_FOUND,
                        "No matching routes were found for %s %s".formatted(context.method(), context.path()),
                        null);
            }
        });
    }
}
