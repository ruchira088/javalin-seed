package com.ruchij.exceptions;

import java.io.Serial;

/**
 * The request body could not be read as the expected JSON. Unchecked because it is raised from inside Javalin's
 * {@code JsonMapper}, whose methods cannot declare checked exceptions.
 */
public class InvalidRequestBodyException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final String MESSAGE = "The request body could not be parsed";

    public InvalidRequestBodyException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
