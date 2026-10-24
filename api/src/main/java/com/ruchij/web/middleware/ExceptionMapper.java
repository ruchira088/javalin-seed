package com.ruchij.web.middleware;

import com.ruchij.exceptions.ResourceConflictException;
import com.ruchij.exceptions.ResourceNotFoundException;
import com.ruchij.exceptions.ValidationException;
import com.ruchij.web.responses.ErrorResponse;
import io.javalin.config.RoutesConfig;
import io.javalin.http.ExceptionHandler;
import io.javalin.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExceptionMapper {
    static final String INTERNAL_SERVER_ERROR_MESSAGE = "An unexpected error occurred";

    private static final Logger logger = LoggerFactory.getLogger(ExceptionMapper.class);

    private static <E extends Exception> ExceptionHandler<E> handle(HttpStatus httpStatus) {
        return (exception, context) -> {
            context.attribute("exception", exception);
            context.status(httpStatus).json(new ErrorResponse(exception.getMessage()));
        };
    }

    // Unexpected failures are logged in full but never echoed to the caller, whose only handle on them is the
    // X-Correlation-ID response header that matches the logged line.
    private static ExceptionHandler<Exception> handleUnexpected() {
        return (exception, context) -> {
            logger.error("Unhandled exception for {} {}", context.method(), context.path(), exception);
            context.attribute("exception", exception);
            context.status(HttpStatus.INTERNAL_SERVER_ERROR).json(new ErrorResponse(INTERNAL_SERVER_ERROR_MESSAGE));
        };
    }

    public static void handle(RoutesConfig routes) {
        routes.exception(ValidationException.class, ExceptionMapper.handle(HttpStatus.BAD_REQUEST));

        routes.exception(ResourceNotFoundException.class, ExceptionMapper.handle(HttpStatus.NOT_FOUND));

        routes.exception(ResourceConflictException.class, ExceptionMapper.handle(HttpStatus.CONFLICT));

        routes.exception(Exception.class, ExceptionMapper.handleUnexpected());

        routes.error(HttpStatus.NOT_FOUND, context -> {
            if (context.endpoint().path.equals("*")) {
                context
                    .status(HttpStatus.NOT_FOUND)
                    .json(
                        new ErrorResponse(
                            "No matching routes were found for %s %s".formatted(context.method(), context.path())
                        )
                    );
            }
        });
    }
}
