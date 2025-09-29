package com.fabricmanagement.user.domain.exception;

/**
 * Exception thrown when a user is not found.
 */
public class UserNotFoundException extends RuntimeException {
    
    public UserNotFoundException(String message) {
        super(message);
    }
    
    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static UserNotFoundException withId(String userId) {
        return new UserNotFoundException("User not found with ID: " + userId);
    }
    
    public static UserNotFoundException withIdentityId(String identityId) {
        return new UserNotFoundException("User not found with identity ID: " + identityId);
    }
}
