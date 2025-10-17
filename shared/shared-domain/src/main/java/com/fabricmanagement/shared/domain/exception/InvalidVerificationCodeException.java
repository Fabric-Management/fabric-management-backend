package com.fabricmanagement.shared.domain.exception;

/**
 * Exception thrown when verification code is invalid
 */
public class InvalidVerificationCodeException extends RuntimeException {
    
    public InvalidVerificationCodeException() {
        super("Invalid verification code");
    }
    
    public InvalidVerificationCodeException(String message) {
        super(message);
    }
}

