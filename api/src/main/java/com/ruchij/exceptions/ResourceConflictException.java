package com.ruchij.exceptions;

import java.io.Serial;

public class ResourceConflictException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceConflictException(String message) {
        super(message);
    }
}
