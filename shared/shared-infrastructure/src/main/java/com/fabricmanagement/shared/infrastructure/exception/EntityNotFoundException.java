package com.fabricmanagement.shared.infrastructure.exception;

/**
 * Entity Not Found Exception
 * 
 * Thrown when a requested entity cannot be found
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
