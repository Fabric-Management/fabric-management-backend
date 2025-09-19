package com.fabricmanagement.identity.domain.exception;

/**
 * Domain exception for identity service.
 */
public class IdentityDomainException extends RuntimeException {

    public IdentityDomainException(String message) {
        super(message);
    }

    public IdentityDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}