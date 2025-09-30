package com.fabricmanagement.shared.infrastructure.exception;

/**
 * Business Rule Violation Exception
 * 
 * Thrown when a business rule is violated during domain operations
 */
public class BusinessRuleViolationException extends RuntimeException {
    
    private final String errorCode;

    public BusinessRuleViolationException(String message) {
        super(message);
        this.errorCode = "BUSINESS_RULE_VIOLATION";
    }

    public BusinessRuleViolationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessRuleViolationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BUSINESS_RULE_VIOLATION";
    }

    public BusinessRuleViolationException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
