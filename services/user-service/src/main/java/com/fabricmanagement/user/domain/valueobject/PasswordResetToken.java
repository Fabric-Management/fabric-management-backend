package com.fabricmanagement.user.domain.valueobject;

import lombok.Value;

import java.time.LocalDateTime;

/**
 * Password Reset Token Value Object
 * 
 * Represents a password reset token with security constraints
 */
@Value
public class PasswordResetToken {
    String token;
    String contactValue;
    ResetMethod resetMethod;
    LocalDateTime expiresAt;
    int attemptsRemaining;
    boolean isUsed;
    
    public enum ResetMethod {
        EMAIL_LINK,
        SMS_CODE,
        EMAIL_CODE
    }
    
    public static PasswordResetToken create(String contactValue, ResetMethod method) {
        return new PasswordResetToken(
            generateToken(method),
            contactValue,
            method,
            LocalDateTime.now().plusMinutes(15), // 15 minutes expiry
            3, // 3 attempts remaining
            false
        );
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return !isExpired() && !isUsed && attemptsRemaining > 0;
    }
    
    public PasswordResetToken consumeAttempt() {
        if (attemptsRemaining <= 1) {
            return new PasswordResetToken(token, contactValue, resetMethod, expiresAt, 0, true);
        }
        return new PasswordResetToken(token, contactValue, resetMethod, expiresAt, attemptsRemaining - 1, isUsed);
    }
    
    public PasswordResetToken markAsUsed() {
        return new PasswordResetToken(token, contactValue, resetMethod, expiresAt, attemptsRemaining, true);
    }
    
    private static String generateToken(ResetMethod method) {
        switch (method) {
            case EMAIL_LINK:
                return java.util.UUID.randomUUID().toString().replace("-", "");
            case SMS_CODE:
            case EMAIL_CODE:
                return String.format("%06d", (int) (Math.random() * 1000000));
            default:
                throw new IllegalArgumentException("Unknown reset method: " + method);
        }
    }
}
