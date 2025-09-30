package com.fabricmanagement.shared.infrastructure.exception;

/**
 * Forbidden Exception
 * 
 * Thrown when authorization fails
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
