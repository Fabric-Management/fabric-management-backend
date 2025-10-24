package com.fabricmanagement.shared.infrastructure.exception;

/**
 * External Service Exception
 * 
 * Thrown when external service calls fail
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
