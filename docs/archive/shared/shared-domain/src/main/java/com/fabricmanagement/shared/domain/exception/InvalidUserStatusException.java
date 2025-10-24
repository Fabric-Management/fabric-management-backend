package com.fabricmanagement.shared.domain.exception;

/**
 * Exception thrown when user status is invalid for requested operation
 */
public class InvalidUserStatusException extends DomainException {
    
    private static final String ERROR_CODE = "INVALID_USER_STATUS";

    public InvalidUserStatusException(String currentStatus, String requiredStatus) {
        super(String.format("Invalid user status. Current: %s, Required: %s", currentStatus, requiredStatus), 
              ERROR_CODE);
    }

    public InvalidUserStatusException(String message) {
        super(message, ERROR_CODE);
    }
}
