package com.fabricmanagement.identity.domain.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Single Responsibility: Two-factor secret management only
 * Open/Closed: Can be extended without modification
 * Immutable: Once created, cannot be modified
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class TwoFactorSecret {
    
    private final String secretKey;
    private final String qrCode;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private final boolean isActive;
    
    /**
     * Creates a new two-factor secret.
     */
    public static TwoFactorSecret create(String secretKey, String qrCode) {
        return TwoFactorSecret.builder()
            .secretKey(secretKey)
            .qrCode(qrCode)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(10)) // 10 minutes to complete setup
            .isActive(false)
            .build();
    }
    
    /**
     * Checks if the secret is expired.
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
    
    /**
     * Checks if the secret is valid for use.
     */
    public boolean isValid() {
        return isActive && !isExpired();
    }
}