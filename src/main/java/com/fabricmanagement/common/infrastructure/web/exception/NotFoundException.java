package com.fabricmanagement.common.infrastructure.web.exception;

/**
 * Exception for resource not found (404).
 */
public class NotFoundException extends RuntimeException {
    
    public NotFoundException(String message) {
        super(message);
    }
    
    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

