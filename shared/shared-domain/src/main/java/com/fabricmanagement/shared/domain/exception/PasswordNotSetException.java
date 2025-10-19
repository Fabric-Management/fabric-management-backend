package com.fabricmanagement.shared.domain.exception;

/**
 * Password Not Set Exception
 * 
 * Thrown when user tries to login but password is not set yet.
 * User should complete password setup first.
 * 
 * HTTP Status: 400 Bad Request
 * Error Code: PASSWORD_NOT_SET
 * 
 * User Action: Complete password setup with verification code
 */
public class PasswordNotSetException extends DomainException {
    
    private static final String ERROR_CODE = "PASSWORD_NOT_SET";

    public PasswordNotSetException() {
        super("Password not set. Please complete password setup first.", ERROR_CODE);
    }

    public PasswordNotSetException(String contactValue) {
        super(String.format("Password not set for %s. Please complete password setup first.", contactValue), 
              ERROR_CODE);
    }

    public PasswordNotSetException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }
}

