package com.fabricmanagement.shared.infrastructure.exception;

/**
 * Unauthorized Exception
 * 
 * Thrown when authentication fails
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
