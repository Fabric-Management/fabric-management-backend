package com.fabricmanagement.identity.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Result of an authentication attempt.
 */
@Getter
@Builder
@AllArgsConstructor
public class AuthenticationResult {

    private final boolean success;
    private final String reason;
    private final boolean requiresTwoFactor;
    private final LocalDateTime lockedUntil;
    private final User authenticatedUser;

    public static AuthenticationResult success(User user) {
        return AuthenticationResult.builder()
            .success(true)
            .authenticatedUser(user)
            .build();
    }

    public static AuthenticationResult requiresTwoFactor(User user) {
        return AuthenticationResult.builder()
            .success(false)
            .requiresTwoFactor(true)
            .authenticatedUser(user)
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

    public static AuthenticationResult contactNotVerified() {
        return AuthenticationResult.builder()
            .success(false)
            .reason("Contact not verified")
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

    public boolean isContactNotVerified() {
        return !success && "Contact not verified".equals(reason);
    }

    public boolean isPasswordChangeRequired() {
        return !success && "Password change required".equals(reason);
    }
}