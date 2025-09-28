package com.fabricmanagement.identity.domain.exception;

/**
 * Base exception for identity domain.
 */
public class IdentityDomainException extends RuntimeException {

    public IdentityDomainException(String message) {
        super(message);
    }

    public IdentityDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}