package com.fabricmanagement.shared.domain.exception;

/**
 * Base Domain Exception
 * 
 * All domain-specific exceptions should extend this class.
 * Provides common error handling and error code management.
 */
public abstract class DomainException extends RuntimeException {
    
    private final String errorCode;
    private final transient Object[] args;

    protected DomainException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }

    protected DomainException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = null;
    }

    protected DomainException(String message, String errorCode, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        return args;
    }
}
