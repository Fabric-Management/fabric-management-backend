package com.fabricmanagement.identity.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Single Responsibility: Credentials representation only
 * Open/Closed: Can be extended without modification
 * Immutable: Once created, cannot be modified
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class Credentials {
    
    private final String username;
    private final String password;
    private final String salt;
    private final String hashedPassword;
    private final LocalDateTime lastPasswordChange;
    private final boolean passwordExpired;
    private final int failedLoginAttempts;
    private final LocalDateTime lockedUntil;
    
    /**
     * Creates new credentials.
     */
    public static Credentials create(String username, String password, String salt, String hashedPassword) {
        return Credentials.builder()
            .username(username)
            .password(password)
            .salt(salt)
            .hashedPassword(hashedPassword)
            .lastPasswordChange(LocalDateTime.now())
            .passwordExpired(false)
            .failedLoginAttempts(0)
            .build();
    }
    
    /**
     * Checks if credentials are locked.
     */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }
    
    /**
     * Checks if password is expired.
     */
    public boolean isPasswordExpired() {
        return passwordExpired;
    }
    
    /**
     * Checks if account is locked due to failed attempts.
     */
    public boolean isAccountLocked() {
        return failedLoginAttempts >= 5 && isLocked();
    }
}