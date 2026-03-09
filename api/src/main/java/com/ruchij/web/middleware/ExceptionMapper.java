package com.ruchij.web.middleware;

import com.ruchij.exceptions.ResourceConflictException;
import com.ruchij.exceptions.ResourceNotFoundException;
import com.ruchij.exceptions.ValidationException;
import com.ruchij.web.responses.ErrorResponse;
import io.javalin.config.RoutesConfig;
import io.javalin.http.ExceptionHandler;
import io.javalin.http.HttpStatus;

public class ExceptionMapper {
    private static <E extends Exception> ExceptionHandler<E> handle(HttpStatus httpStatus) {
        return (exception, context) -> {
            context.attribute("exception", exception);
            context.status(httpStatus).json(new ErrorResponse(exception.getMessage()));
        };
    }

    public static void handle(RoutesConfig routes) {
        routes.exception(ValidationException.class, ExceptionMapper.handle(HttpStatus.BAD_REQUEST));

        routes.exception(ResourceNotFoundException.class, ExceptionMapper.handle(HttpStatus.NOT_FOUND));

        routes.exception(ResourceConflictException.class, ExceptionMapper.handle(HttpStatus.CONFLICT));

        routes.exception(Exception.class, ExceptionMapper.handle(HttpStatus.INTERNAL_SERVER_ERROR));

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
