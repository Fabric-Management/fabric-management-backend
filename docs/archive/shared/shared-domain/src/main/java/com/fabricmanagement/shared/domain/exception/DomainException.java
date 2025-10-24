package com.fabricmanagement.shared.domain.exception;

import lombok.Getter;

/**
 * Domain Exception
 * 
 * Base exception for domain-specific errors
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ DOMAIN EXCEPTION
 * ✅ EXTENDS RUNTIME EXCEPTION
 */
@Getter
public class DomainException extends RuntimeException {
    
    private final String errorCode;
    private final Object[] args;
    
    public DomainException(String message) {
        super(message);
        this.errorCode = null;
        this.args = null;
    }
    
    public DomainException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.args = null;
    }
    
    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }
    
    public DomainException(String errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }
    
    public DomainException(String errorCode, String message, Throwable cause, Object... args) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = args;
    }
}