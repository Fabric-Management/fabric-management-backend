package com.fabricmanagement.user.domain.valueobject;

import com.fabricmanagement.shared.domain.base.BaseEntity;
import com.fabricmanagement.shared.infrastructure.constants.TokenConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Password Reset Token Entity
 * 
 * Represents a password reset token with security constraints
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PasswordResetToken extends BaseEntity {
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "contact_value", nullable = false)
    private String contactValue;
    
    @Column(name = "token", nullable = false, unique = true)
    private String token;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "reset_method", nullable = false)
    private ResetMethod resetMethod;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(name = "attempts_remaining", nullable = false)
    private int attemptsRemaining;
    
    @Column(name = "is_used", nullable = false)
    private boolean isUsed;
    
    @Column(name = "used_at")
    private LocalDateTime usedAt;
    
    public enum ResetMethod {
        EMAIL_LINK,
        SMS_CODE,
        EMAIL_CODE
    }
    
    /**
     * Creates a new password reset token with default expiry and attempts
     */
    public static PasswordResetToken create(UUID userId, String contactValue, ResetMethod method) {
        return create(userId, contactValue, method, 
            TokenConstants.DEFAULT_RESET_TOKEN_EXPIRY_MINUTES, 
            TokenConstants.DEFAULT_RESET_TOKEN_ATTEMPTS);
    }
    
    /**
     * Creates a new password reset token with custom expiry and attempts
     */
    public static PasswordResetToken create(UUID userId, String contactValue, ResetMethod method, 
                                           int expiryMinutes, int attempts) {
        return PasswordResetToken.builder()
            .userId(userId)
            .contactValue(contactValue)
            .token(generateToken(method))
            .resetMethod(method)
            .expiresAt(LocalDateTime.now().plusMinutes(expiryMinutes))
            .attemptsRemaining(attempts)
            .isUsed(false)
            .build();
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return !isExpired() && !isUsed && attemptsRemaining > 0;
    }
    
    public PasswordResetToken consumeAttempt() {
        if (attemptsRemaining <= 1) {
        return PasswordResetToken.builder()
            .id(this.getId())
            .userId(this.userId)
            .contactValue(this.contactValue)
            .token(this.token)
            .resetMethod(this.resetMethod)
            .expiresAt(this.expiresAt)
            .attemptsRemaining(0)
            .isUsed(true)
            .usedAt(LocalDateTime.now())
            .build();
        }
        return PasswordResetToken.builder()
            .id(this.getId())
            .userId(this.userId)
            .contactValue(this.contactValue)
            .token(this.token)
            .resetMethod(this.resetMethod)
            .expiresAt(this.expiresAt)
            .attemptsRemaining(this.attemptsRemaining - 1)
            .isUsed(this.isUsed)
            .usedAt(this.usedAt)
            .build();
    }
    
    public PasswordResetToken markAsUsed() {
        return PasswordResetToken.builder()
            .id(this.getId())
            .userId(this.userId)
            .contactValue(this.contactValue)
            .token(this.token)
            .resetMethod(this.resetMethod)
            .expiresAt(this.expiresAt)
            .attemptsRemaining(this.attemptsRemaining)
            .isUsed(true)
            .usedAt(LocalDateTime.now())
            .build();
    }
    
    private static String generateToken(ResetMethod method) {
        switch (method) {
            case EMAIL_LINK:
                return java.util.UUID.randomUUID().toString().replace("-", "");
            case SMS_CODE:
            case EMAIL_CODE:
                // Use ThreadLocalRandom for better random number generation
                int code = ThreadLocalRandom.current().nextInt(
                    TokenConstants.MIN_RANDOM_CODE, 
                    TokenConstants.MAX_RANDOM_CODE + 1
                );
                return String.format("%0" + TokenConstants.RESET_TOKEN_CODE_LENGTH + "d", code);
            default:
                throw new IllegalArgumentException("Unknown reset method: " + method);
        }
    }
}
