package com.fabricmanagement.shared.domain.exception;

/**
 * Exception thrown when a user is not found
 */
public class UserNotFoundException extends DomainException {
    
    private static final String ERROR_CODE = "USER_NOT_FOUND";

    public UserNotFoundException(String userId) {
        super(String.format("User not found: %s", userId), ERROR_CODE);
    }

    public UserNotFoundException(String userId, Throwable cause) {
        super(String.format("User not found: %s", userId), ERROR_CODE, cause);
    }
}
