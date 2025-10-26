package com.fabricmanagement.common.platform.auth.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Registration Token - Secure token for email-based registration flow.
 *
 * <p>Used in two scenarios:
 * <ul>
 *   <li><b>Sales-led onboarding:</b> Token-only (email verified by click)</li>
 *   <li><b>Self-service signup:</b> Token + verification code (double security)</li>
 * </ul>
 *
 * <h2>Security:</h2>
 * <ul>
 *   <li>Token is UUID - globally unique, unguessable</li>
 *   <li>24-hour expiry for security</li>
 *   <li>Single-use only</li>
 *   <li>Linked to specific contact value</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * // Create token
 * RegistrationToken token = RegistrationToken.create(
 *     "user@example.com",
 *     RegistrationTokenType.SALES_LED
 * );
 *
 * // Validate token
 * if (token.isValid()) {
 *     token.markAsUsed();
 * }
 * }</pre>
 */
@Entity
@Table(name = "common_registration_token", schema = "common_auth")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationToken extends BaseEntity {

    @Column(name = "token", nullable = false, unique = true, length = 36)
    private String token;

    @Column(name = "contact_value", nullable = false, length = 255)
    private String contactValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false)
    private RegistrationTokenType tokenType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "company_id")
    private UUID companyId;

    /**
     * Create new registration token.
     *
     * @param contactValue Email or phone
     * @param tokenType Token type
     * @return New registration token
     */
    public static RegistrationToken create(String contactValue, RegistrationTokenType tokenType) {
        return RegistrationToken.builder()
            .token(UUID.randomUUID().toString())
            .contactValue(contactValue)
            .tokenType(tokenType)
            .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
            .isUsed(false)
            .build();
    }

    /**
     * Check if token is valid.
     *
     * @return true if valid (not expired, not used)
     */
    public boolean isValid() {
        return !isUsed && expiresAt.isAfter(Instant.now());
    }

    /**
     * Check if token is expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    /**
     * Mark token as used.
     */
    public void markAsUsed() {
        this.isUsed = true;
        this.usedAt = Instant.now();
    }

    /**
     * Link token to user and company.
     *
     * @param userId User ID
     * @param companyId Company ID
     */
    public void linkTo(UUID userId, UUID companyId) {
        this.userId = userId;
        this.companyId = companyId;
    }

    @Override
    protected String getModuleCode() {
        return "REGT";
    }
}

