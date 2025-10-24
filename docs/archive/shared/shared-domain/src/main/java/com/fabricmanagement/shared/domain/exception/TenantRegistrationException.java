package com.fabricmanagement.shared.domain.exception;

public class TenantRegistrationException extends RuntimeException {
    
    public TenantRegistrationException(String message) {
        super(message);
    }
    
    public TenantRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}

