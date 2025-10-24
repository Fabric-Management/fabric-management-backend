package com.fabricmanagement.shared.infrastructure.exception;

/**
 * Duplicate Entity Exception
 * 
 * Thrown when trying to create an entity that already exists
 */
public class DuplicateEntityException extends RuntimeException {

    public DuplicateEntityException(String message) {
        super(message);
    }

    public DuplicateEntityException(String message, Throwable cause) {
        super(message, cause);
    }
}
