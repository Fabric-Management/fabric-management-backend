package com.fabricmanagement.identity.domain.exception;

/**
 * Single Responsibility: Identity domain exception representation only
 * Open/Closed: Can be extended without modification
 * Base exception for all identity-related domain errors
 */
public class IdentityDomainException extends RuntimeException {
    
    public IdentityDomainException(String message) {
        super(message);
    }
    
    public IdentityDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}