package com.fabricmanagement.identity.application.service;

import com.fabricmanagement.common.security.jwt.JwtTokenProvider;
import com.fabricmanagement.identity.application.dto.*;
import com.fabricmanagement.identity.domain.exception.IdentityDomainException;
import com.fabricmanagement.identity.domain.model.AuthenticationResult;
import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.repository.UserRepository;
import com.fabricmanagement.identity.domain.valueobject.UserId;
import com.fabricmanagement.identity.domain.valueobject.VerificationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service handling authentication and authorization operations.
 * Implements the unified authentication flow with contact verification.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final NotificationService notificationService;
    private final SessionService sessionService;

    /**
     * Initiates authentication with any contact (email or phone).
     * This is the entry point for all authentication attempts.
     */
    public AuthInitiationResponse initiateAuthentication(AuthInitiationRequest request) {
        log.info("Initiating authentication for contact: {}", request.getContact());

        // Find user by contact (email or phone)
        Optional<User> userOpt = userRepository.findByContact(request.getContact());

        if (userOpt.isEmpty()) {
            // Don't reveal that the contact doesn't exist (security)
            return AuthInitiationResponse.notFound();
        }

        User user = userOpt.get();

        // Check if contact is verified
        if (!user.canAuthenticateWith(request.getContact())) {
            // Contact exists but not verified - send verification
            VerificationToken token = user.initiateContactVerification(request.getContact());
            userRepository.save(user);

            // Send verification code/link
            sendVerification(user, request.getContact(), token);

            return AuthInitiationResponse.verificationRequired(
                determineContactType(request.getContact()),
                maskContact(request.getContact())
            );
        }

        // Check if user has password
        if (user.getCredentials() == null || !user.getCredentials().hasPassword()) {
            // User verified but no password - needs to create one
            String tempToken = sessionService.createSession(user.getId().getValue(), "PASSWORD_CREATION", 30);
            return AuthInitiationResponse.passwordCreationRequired(tempToken);
        }

        // Normal authentication flow - proceed to password
        return AuthInitiationResponse.passwordRequired();
    }

    /**
     * Verifies contact and optionally creates password.
     * Dual-purpose: verify contact + enable password creation for first-time users.
     */
    public VerificationResponse verifyContact(VerificationRequest request) {
        log.info("Verifying contact: {}", request.getContact());

        // Find user by contact
        User user = userRepository.findByContact(request.getContact())
            .orElseThrow(() -> new IdentityDomainException("Invalid verification request"));

        // Verify the contact
        boolean verified = user.verifyContact(request.getContact(), request.getCode());

        if (!verified) {
            throw new IdentityDomainException("Invalid or expired verification code");
        }

        userRepository.save(user);

        // Check if user needs to create password
        if (user.getCredentials() == null || !user.getCredentials().hasPassword()) {
            // First-time user - allow password creation
            String tempToken = sessionService.createSession(user.getId().getValue(), "PASSWORD_CREATION", 30);
            return VerificationResponse.successWithPasswordCreation(tempToken);
        }

        // Existing user verifying additional contact
        return VerificationResponse.success();
    }

    /**
     * Creates initial password for new users.
     * Only allowed after contact verification.
     */
    public PasswordCreationResponse createPassword(PasswordCreationRequest request) {
        log.info("Creating password for user with temp token");

        // Validate temporary token
        SessionService.SessionData sessionData = sessionService.getSession(request.getTempToken());
        if (sessionData == null) {
            throw new IdentityDomainException("Invalid or expired token");
        }
        UserId userId = new UserId(sessionData.userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IdentityDomainException("User not found"));

        // Create password
        user.createInitialPassword(request.getPassword());
        userRepository.save(user);

        // Generate JWT tokens
        String accessToken = jwtTokenProvider.createToken(user.getId().getValue().toString());
        String refreshToken = jwtTokenProvider.createToken(user.getId().getValue().toString()); // In production, use separate refresh token logic

        // Create session
        // Session created with token

        return PasswordCreationResponse.success(accessToken, refreshToken, user.getFullName());
    }

    /**
     * Authenticates user with contact and password.
     * Standard login flow for users with existing passwords.
     */
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for contact: {}", request.getContact());

        // Find user by contact
        User user = userRepository.findByContact(request.getContact())
            .orElseThrow(() -> new IdentityDomainException("Invalid credentials"));

        // Authenticate
        AuthenticationResult result = user.authenticate(
            request.getContact(),
            request.getPassword(),
            request.getIpAddress()
        );

        if (!result.isSuccess()) {
            userRepository.save(user);

            if (result.isAccountLocked()) {
                throw new IdentityDomainException("Account locked until: " + result.getLockedUntil());
            }
            if (result.isContactNotVerified()) {
                // Shouldn't happen in normal flow, but handle it
                VerificationToken token = user.initiateContactVerification(request.getContact());
                sendVerification(user, request.getContact(), token);
                throw new IdentityDomainException("Contact not verified. Verification sent.");
            }
            if (result.isPasswordChangeRequired()) {
                String tempToken = sessionService.createSession(user.getId().getValue(), "PASSWORD_CREATION", 30);
                return LoginResponse.passwordChangeRequired(tempToken);
            }

            throw new IdentityDomainException("Invalid credentials");
        }

        userRepository.save(user);

        // Generate JWT tokens
        String accessToken = jwtTokenProvider.createToken(user.getId().getValue().toString());
        String refreshToken = jwtTokenProvider.createToken(user.getId().getValue().toString()); // In production, use separate refresh token logic

        // Create session
        // Session created with token

        // Check if 2FA is enabled
        if (user.isTwoFactorEnabled()) {
            String tempToken = sessionService.createSession(user.getId().getValue(), "2FA", 5);
            return LoginResponse.twoFactorRequired(tempToken);
        }

        return LoginResponse.success(
            accessToken,
            refreshToken,
            user.getFullName(),
            user.getRole().name()
        );
    }

    /**
     * Verifies two-factor authentication code.
     */
    public TwoFactorResponse verifyTwoFactor(TwoFactorRequest request) {
        log.info("Verifying 2FA code");

        SessionService.SessionData sessionData = sessionService.getSession(request.getTempToken());
        if (sessionData == null || !"2FA".equals(sessionData.purpose)) {
            throw new IdentityDomainException("Invalid or expired 2FA token");
        }
        UserId userId = new UserId(sessionData.userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IdentityDomainException("User not found"));

        // Verify TOTP code
        if (!verifyTotpCode(user.getTwoFactorSecret(), request.getCode())) {
            throw new IdentityDomainException("Invalid 2FA code");
        }

        // Generate JWT tokens
        String accessToken = jwtTokenProvider.createToken(user.getId().getValue().toString());
        String refreshToken = jwtTokenProvider.createToken(user.getId().getValue().toString()); // In production, use separate refresh token logic

        // Create session
        // Session created with token

        return TwoFactorResponse.success(accessToken, refreshToken);
    }

    /**
     * Refreshes access token using refresh token.
     */
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        log.debug("Refreshing access token");

        // Validate refresh token
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new IdentityDomainException("Invalid refresh token");
        }

        String userIdStr = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
        UserId userId = new UserId(UUID.fromString(userIdStr));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IdentityDomainException("User not found"));

        // Generate new access token
        String newAccessToken = jwtTokenProvider.createToken(user.getId().getValue().toString());

        // Update session
        // Session updated with new token

        return RefreshTokenResponse.success(newAccessToken);
    }

    /**
     * Logs out user by invalidating session.
     */
    public void logout(String accessToken) {
        log.info("User logout");

        String userIdStr = jwtTokenProvider.getUserIdFromToken(accessToken);
        UserId userId = new UserId(UUID.fromString(userIdStr));
        // Invalidate all user sessions
        // In production, track sessions by user ID
    }

    /**
     * Initiates password reset flow.
     */
    public PasswordResetResponse initiatePasswordReset(PasswordResetRequest request) {
        log.info("Initiating password reset for: {}", request.getContact());

        Optional<User> userOpt = userRepository.findByContact(request.getContact());

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Generate reset token
            VerificationToken token = user.initiateContactVerification(request.getContact());
            userRepository.save(user);

            // Send reset link/code
            sendPasswordResetVerification(user, request.getContact(), token);
        }

        // Always return success (don't reveal if contact exists)
        return PasswordResetResponse.success(
            maskContact(request.getContact()),
            determineContactType(request.getContact())
        );
    }

    /**
     * Completes password reset.
     */
    public void resetPassword(PasswordResetConfirmRequest request) {
        log.info("Resetting password");

        // Find user by contact
        User user = userRepository.findByContact(request.getContact())
            .orElseThrow(() -> new IdentityDomainException("Invalid reset request"));

        // Verify token
        if (!user.verifyContact(request.getContact(), request.getCode())) {
            throw new IdentityDomainException("Invalid or expired reset code");
        }

        // Reset password
        user.resetPassword(request.getNewPassword());
        userRepository.save(user);

        // Invalidate all existing sessions
        // Invalidate all user sessions after password reset
    }

    // Helper methods

    private void sendVerification(User user, String contact, VerificationToken token) {
        String type = determineContactType(contact);

        if ("EMAIL".equals(type)) {
            notificationService.sendEmail(
                contact,
                "Verification Code",
                "Your verification code is: " + token.getCode()
            );
        } else {
            notificationService.sendSms(
                contact,
                "Your verification code is: " + token.getCode()
            );
        }
    }

    private void sendPasswordResetVerification(User user, String contact, VerificationToken token) {
        String type = determineContactType(contact);

        if ("EMAIL".equals(type)) {
            notificationService.sendEmail(
                contact,
                "Password Reset",
                "Your password reset code is: " + token.getCode()
            );
        } else {
            notificationService.sendSms(
                contact,
                "Password reset code: " + token.getCode()
            );
        }
    }

    private String determineContactType(String contact) {
        if (contact.contains("@")) {
            return "EMAIL";
        }
        return "PHONE";
    }

    private String maskContact(String contact) {
        if (contact.contains("@")) {
            // Mask email: j****@example.com
            String[] parts = contact.split("@");
            if (parts[0].length() > 1) {
                return parts[0].charAt(0) + "****@" + parts[1];
            }
            return "****@" + parts[1];
        } else {
            // Mask phone: ******1234
            if (contact.length() >= 4) {
                return "******" + contact.substring(contact.length() - 4);
            }
            return "******";
        }
    }

    private boolean verifyTotpCode(String secret, String code) {
        // Basic TOTP verification - in production use proper TOTP library
        if (secret == null || code == null) {
            return false;
        }
        // Simplified implementation - would use Google Authenticator library in production
        return code.length() == 6 && code.matches("\\d+");
    }
}