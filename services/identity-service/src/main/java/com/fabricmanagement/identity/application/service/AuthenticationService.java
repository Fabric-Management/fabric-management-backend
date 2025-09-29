package com.fabricmanagement.identity.application.service;

import com.fabricmanagement.identity.application.dto.auth.LoginRequest;
import com.fabricmanagement.identity.application.dto.auth.LoginResponse;
import com.fabricmanagement.identity.application.dto.auth.RefreshTokenRequest;
import com.fabricmanagement.identity.application.dto.auth.RefreshTokenResponse;
import com.fabricmanagement.identity.application.dto.auth.ChangePasswordRequest;
import com.fabricmanagement.identity.application.dto.auth.ForgotPasswordRequest;
import com.fabricmanagement.identity.application.dto.auth.ResetPasswordRequest;
import com.fabricmanagement.identity.application.dto.auth.TwoFactorRequest;
import com.fabricmanagement.identity.application.dto.auth.TwoFactorResponse;
import com.fabricmanagement.identity.application.port.in.command.AuthenticationUseCase;
import com.fabricmanagement.identity.application.port.out.ExternalIdentityServicePort;
import com.fabricmanagement.identity.application.port.out.IdentityEventPublisherPort;
import com.fabricmanagement.identity.application.port.out.UserServicePort;
import com.fabricmanagement.identity.domain.model.AuthenticationResult;
import com.fabricmanagement.identity.domain.model.Session;
import com.fabricmanagement.identity.domain.event.PasswordChangedEvent;
import com.fabricmanagement.identity.domain.event.TwoFactorEnabledEvent;
import com.fabricmanagement.identity.domain.event.TwoFactorDisabledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single Responsibility: Authentication operations only
 * Open/Closed: Can be extended without modification
 * Dependency Inversion: Depends on abstractions, not implementations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthenticationService implements AuthenticationUseCase {

    private final ExternalIdentityServicePort externalIdentityServicePort;
    private final IdentityEventPublisherPort identityEventPublisherPort;
    private final UserServicePort userServicePort;
    private final SessionService sessionService;

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("Attempting login for user: {}", request.getUsername());
        
        // Authenticate user
        AuthenticationResult result = authenticateUser(request.getUsername(), request.getPassword());
        
        if (!result.isSuccess()) {
            throw new IllegalArgumentException(result.getReason());
        }
        
        // Check if two-factor is required
        if (result.isRequiresTwoFactor()) {
            return LoginResponse.builder()
                .requiresTwoFactor(true)
                .userId(result.getUserId())
                .username(result.getUsername())
                .email(result.getEmail())
                .role(result.getRole())
                .build();
        }
        
        // Create session
        Session session = sessionService.createSession(
            result.getUserId(),
            request.getUsername(),
            result.getEmail(),
            result.getRole()
        );
        
        return LoginResponse.builder()
            .accessToken(session.getAccessToken())
            .refreshToken(session.getRefreshToken())
            .tokenType("Bearer")
            .expiresIn(3600L) // 1 hour
            .expiresAt(session.getExpiresAt())
            .userId(result.getUserId())
            .username(result.getUsername())
            .email(result.getEmail())
            .role(result.getRole())
            .build();
    }

    @Override
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refreshing token");
        
        Session session = sessionService.refreshSession(request.getRefreshToken());
        
        return RefreshTokenResponse.builder()
            .accessToken(session.getAccessToken())
            .refreshToken(session.getRefreshToken())
            .tokenType("Bearer")
            .expiresIn(3600L) // 1 hour
            .expiresAt(session.getExpiresAt())
            .build();
    }

    @Override
    public void logout(String refreshToken) {
        log.info("Logging out user");
        sessionService.invalidateSession(refreshToken);
    }

    @Override
    public void changePassword(String userId, ChangePasswordRequest request) {
        log.info("Changing password for user: {}", userId);
        
        // Validate current password
        if (!validateCurrentPassword(userId, request.getCurrentPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        
        // Validate new password
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }
        
        // Update password
        updatePassword(userId, request.getNewPassword());
        
        // Publish event
        identityEventPublisherPort.publish(new PasswordChangedEvent(userId));
        
        log.info("Password changed successfully for user: {}", userId);
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("Initiating password reset for email: {}", request.getEmail());
        
        // Send password reset email
        externalIdentityServicePort.sendEmail(
            request.getEmail(),
            "Password Reset",
            "Click the link to reset your password"
        );
        
        log.info("Password reset email sent to: {}", request.getEmail());
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Resetting password with token");
        
        // Validate reset token
        if (!validateResetToken(request.getResetToken())) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }
        
        // Validate new password
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }
        
        // Get user ID from token
        String userId = getUserIdFromResetToken(request.getResetToken());
        
        // Update password
        updatePassword(userId, request.getNewPassword());
        
        // Publish event
        identityEventPublisherPort.publish(new PasswordChangedEvent(userId));
        
        log.info("Password reset successfully for user: {}", userId);
    }

    @Override
    public TwoFactorResponse validateTwoFactor(TwoFactorRequest request) {
        log.info("Validating two-factor code for user: {}", request.getUserId());
        
        // Validate two-factor code
        if (!validateTwoFactorCode(request.getUserId(), request.getCode())) {
            throw new IllegalArgumentException("Invalid two-factor code");
        }
        
        // Get user profile
        var userProfile = userServicePort.getUserProfile(request.getUserId());
        
        // Create session
        Session session = sessionService.createSession(
            request.getUserId(),
            userProfile.username(),
            userProfile.email(),
            userProfile.status()
        );
        
        return TwoFactorResponse.builder()
            .success(true)
            .message("Two-factor authentication successful")
            .build();
    }

    @Override
    public String enableTwoFactor(String userId) {
        log.info("Enabling two-factor authentication for user: {}", userId);
        
        // Generate two-factor secret
        String secretKey = generateTwoFactorSecret();
        String qrCode = generateQRCode(secretKey, userId);
        
        // Save two-factor secret
        saveTwoFactorSecret(userId, secretKey);
        
        // Publish event
        identityEventPublisherPort.publish(new TwoFactorEnabledEvent(userId));
        
        log.info("Two-factor authentication enabled for user: {}", userId);
        return qrCode;
    }

    @Override
    public void disableTwoFactor(String userId) {
        log.info("Disabling two-factor authentication for user: {}", userId);
        
        // Remove two-factor secret
        removeTwoFactorSecret(userId);
        
        // Publish event
        identityEventPublisherPort.publish(new TwoFactorDisabledEvent(userId));
        
        log.info("Two-factor authentication disabled for user: {}", userId);
    }

    // Private helper methods
    private AuthenticationResult authenticateUser(String username, String password) {
        // Implementation would authenticate against external service
        // For now, return a mock result
        return AuthenticationResult.success("user123", username, "user@example.com", "USER");
    }

    private boolean validateCurrentPassword(String userId, String currentPassword) {
        // Implementation would validate current password
        return true;
    }

    private void updatePassword(String userId, String newPassword) {
        // Implementation would update password
    }

    private boolean validateResetToken(String resetToken) {
        // Implementation would validate reset token
        return true;
    }

    private String getUserIdFromResetToken(String resetToken) {
        // Implementation would extract user ID from token
        return "user123";
    }

    private boolean validateTwoFactorCode(String userId, String code) {
        // Implementation would validate two-factor code
        return true;
    }

    private String generateTwoFactorSecret() {
        // Implementation would generate two-factor secret
        return "secret123";
    }

    private String generateQRCode(String secretKey, String userId) {
        // Implementation would generate QR code
        return "qr123";
    }

    private void saveTwoFactorSecret(String userId, String secretKey) {
        // Implementation would save two-factor secret
    }

    private void removeTwoFactorSecret(String userId) {
        // Implementation would remove two-factor secret
    }
}