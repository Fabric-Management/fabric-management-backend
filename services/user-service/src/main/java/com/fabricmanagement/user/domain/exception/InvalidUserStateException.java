package com.fabricmanagement.user.domain.exception;

/**
 * Exception thrown when a user state transition is invalid.
 */
public class InvalidUserStateException extends RuntimeException {
    
    public InvalidUserStateException(String message) {
        super(message);
    }
    
    public InvalidUserStateException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static InvalidUserStateException invalidTransition(String fromState, String toState) {
        return new InvalidUserStateException(
            String.format("Invalid state transition from %s to %s", fromState, toState)
        );
    }
    
    public static InvalidUserStateException alreadyInState(String currentState) {
        return new InvalidUserStateException("User is already in state: " + currentState);
    }
}
