package com.fabricmanagement.common.platform.auth.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Verification Code entity for multi-channel verification.
 *
 * <p>Used for:
 * <ul>
 *   <li>Registration verification</li>
 *   <li>Password reset</li>
 *   <li>Email/Phone verification</li>
 * </ul>
 *
 * <h2>Security:</h2>
 * <ul>
 *   <li>6-digit code (random)</li>
 *   <li>10-minute expiry</li>
 *   <li>Max 3 verification attempts</li>
 *   <li>One-time use only</li>
 * </ul>
 */
@Entity
@Table(name = "common_verification_code", schema = "common_auth",
    indexes = {
        @Index(name = "idx_verification_contact", columnList = "contact_value"),
        @Index(name = "idx_verification_code", columnList = "code")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationCode extends BaseEntity {

    @Column(name = "contact_value", nullable = false, length = 255)
    private String contactValue;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private VerificationType type;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    public static VerificationCode create(String contactValue, String code, VerificationType type, int expiryMinutes) {
        return VerificationCode.builder()
            .contactValue(contactValue)
            .code(code)
            .type(type)
            .expiresAt(Instant.now().plusSeconds(expiryMinutes * 60L))
            .isUsed(false)
            .attemptCount(0)
            .build();
    }

    public boolean isExpired() {
        return this.expiresAt.isBefore(Instant.now());
    }

    public boolean isValid() {
        return !this.isUsed && !isExpired() && this.attemptCount < 3;
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }

    public void markAsUsed() {
        this.isUsed = true;
        this.usedAt = Instant.now();
    }

    @Override
    protected String getModuleCode() {
        return "VERIFY";
    }
}

