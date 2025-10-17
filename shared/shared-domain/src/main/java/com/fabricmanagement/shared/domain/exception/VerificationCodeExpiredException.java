package com.fabricmanagement.shared.domain.exception;

/**
 * Exception thrown when verification code has expired
 */
public class VerificationCodeExpiredException extends RuntimeException {
    
    public VerificationCodeExpiredException() {
        super("Verification code has expired");
    }
    
    public VerificationCodeExpiredException(String message) {
        super(message);
    }
}

