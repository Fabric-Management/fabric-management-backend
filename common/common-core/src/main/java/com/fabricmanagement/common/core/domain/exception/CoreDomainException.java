package com.fabricmanagement.common.core.domain.exception;

/**
 * Base domain exception for all microservices
 * All domain-specific exceptions should extend this class
 */
public class CoreDomainException extends RuntimeException {

    private final String errorCode;

    public CoreDomainException(String message) {
        super(message);
        this.errorCode = "DOMAIN_ERROR";
    }

    public CoreDomainException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public CoreDomainException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "DOMAIN_ERROR";
    }

    public CoreDomainException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
