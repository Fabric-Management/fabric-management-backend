package com.fabricmanagement.common.core.domain.exception;

/**
 * Base exception for all domain-related exceptions.
 * This serves as the root exception for the domain layer.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
