package com.fabricmanagement.identity.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Single Responsibility: Authentication result representation only
 * Open/Closed: Can be extended without modification
 */
@Getter
@Builder
@AllArgsConstructor
public class AuthenticationResult {

    private final boolean success;
    private final String reason;
    private final boolean requiresTwoFactor;
    private final LocalDateTime lockedUntil;
    private final String userId;
    private final String username;
    private final String email;
    private final String role;

    public static AuthenticationResult success(String userId, String username, String email, String role) {
        return AuthenticationResult.builder()
            .success(true)
            .userId(userId)
            .username(username)
            .email(email)
            .role(role)
            .build();
    }

    public static AuthenticationResult requiresTwoFactor(String userId, String username, String email, String role) {
        return AuthenticationResult.builder()
            .success(false)
            .requiresTwoFactor(true)
            .userId(userId)
            .username(username)
            .email(email)
            .role(role)
            .reason("Two-factor authentication required")
            .build();
    }

    public static AuthenticationResult invalidCredentials() {
        return AuthenticationResult.builder()
            .success(false)
            .reason("Invalid credentials")
            .build();
    }

    public static AuthenticationResult accountLocked(LocalDateTime until) {
        return AuthenticationResult.builder()
            .success(false)
            .reason("Account locked")
            .lockedUntil(until)
            .build();
    }

    public static AuthenticationResult accountInactive() {
        return AuthenticationResult.builder()
            .success(false)
            .reason("Account inactive")
            .build();
    }

    public static AuthenticationResult passwordChangeRequired() {
        return AuthenticationResult.builder()
            .success(false)
            .reason("Password change required")
            .build();
    }

    public boolean isAccountLocked() {
        return !success && lockedUntil != null;
    }

    public boolean isPasswordChangeRequired() {
        return !success && "Password change required".equals(reason);
    }
}