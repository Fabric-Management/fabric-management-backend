package com.fabricmanagement.shared.domain.exception;

/**
 * Exception thrown when trying to setup password for user who already has one
 */
public class PasswordAlreadySetException extends DomainException {
    
    private static final String ERROR_CODE = "PASSWORD_ALREADY_SET";

    public PasswordAlreadySetException() {
        super("Password already set. Please use login instead.", ERROR_CODE);
    }

    public PasswordAlreadySetException(String contactValue) {
        super(String.format("Password already set for contact: %s. Please use login instead.", contactValue), 
              ERROR_CODE);
    }
}
