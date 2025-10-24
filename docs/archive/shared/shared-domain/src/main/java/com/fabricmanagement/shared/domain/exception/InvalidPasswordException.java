package com.fabricmanagement.shared.domain.exception;

/**
 * Exception thrown when password validation fails
 */
public class InvalidPasswordException extends DomainException {
    
    private static final String ERROR_CODE = "INVALID_PASSWORD";

    public InvalidPasswordException() {
        super("Invalid password", ERROR_CODE);
    }

    public InvalidPasswordException(String message) {
        super(message, ERROR_CODE);
    }

    public InvalidPasswordException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }
}
