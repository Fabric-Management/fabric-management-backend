package com.fabricmanagement.identity.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Single Responsibility: Session management only
 * Open/Closed: Can be extended without modification
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class Session {

    private UUID id;
    private String userId;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;
    private String ipAddress;
    private String userAgent;
    private boolean isActive;

    /**
     * Creates a new session.
     */
    public static Session create(String userId, String accessToken, String refreshToken, 
                               LocalDateTime expiresAt, String ipAddress, String userAgent) {
        return Session.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresAt(expiresAt)
            .createdAt(LocalDateTime.now())
            .lastAccessedAt(LocalDateTime.now())
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .isActive(true)
            .build();
    }

    /**
     * Checks if session is expired.
     */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    /**
     * Updates last accessed time.
     */
    public void updateLastAccessed() {
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * Invalidates the session.
     */
    public void invalidate() {
        this.isActive = false;
    }

    /**
     * Checks if session is valid.
     */
    public boolean isValid() {
        return isActive && !isExpired();
    }
}