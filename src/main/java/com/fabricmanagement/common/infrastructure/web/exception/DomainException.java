package com.fabricmanagement.common.infrastructure.web.exception;

/**
 * Exception for domain rule violations (400).
 */
public class DomainException extends RuntimeException {
    
    public DomainException(String message) {
        super(message);
    }
    
    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

