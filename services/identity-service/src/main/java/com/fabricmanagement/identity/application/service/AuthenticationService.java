package com.fabricmanagement.identity.application.service;

import com.fabricmanagement.identity.application.dto.auth.*;
import com.fabricmanagement.identity.domain.model.AuthenticationResult;
import com.fabricmanagement.identity.domain.model.User;
import com.fabricmanagement.identity.domain.repository.UserRepository;
import com.fabricmanagement.identity.domain.valueobject.ContactType;
import com.fabricmanagement.identity.domain.valueobject.UserRole;
import com.fabricmanagement.identity.infrastructure.security.JwtTokenProvider;
import com.fabricmanagement.identity.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Authentication service for handling user authentication operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthenticationService {
    
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final NotificationService notificationService;
    private final SessionService sessionService;
    
    /**
     * Registers a new user.
     */
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with username: {}", request.getUsername());
        
        // Check if user already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userRepository.existsByContactValue(request.getPhone())) {
            throw new IllegalArgumentException("Phone number already exists");
        }

        // Create user
        UUID tenantId = request.getTenantId() != null ? UUID.fromString(request.getTenantId()) : UUID.randomUUID();
        User user = User.create(
            tenantId,
            request.getUsername(),
            request.getFirstName(),
            request.getLastName(),
            UserRole.USER,
            "system"
        );

        // Add contacts
        user.addContact(ContactType.EMAIL, request.getEmail(), "system");
        user.addContact(ContactType.PHONE, request.getPhone(), "system");

        // Save user
        user = userRepository.save(user);

        // Initiate email verification
        user.initiateContactVerification(request.getEmail());
        userRepository.save(user);

        // Send verification email
        notificationService.sendVerificationEmail(request.getEmail(), user.getPendingVerifications().values().iterator().next());

        log.info("User registered successfully with ID: {}", user.getId());

        return AuthResponse.builder()
            .userId(user.getId().getValue().toString())
            .username(user.getUsername())
            .email(request.getEmail())
            .role(user.getRole().name())
            .twoFactorRequired(false)
            .passwordChangeRequired(false)
            .build();
    }

    /**
     * Authenticates a user.
     */
    public AuthResponse login(LoginRequest request, String ipAddress) {
        log.info("Login attempt for contact: {}", request.getContactValue());

        // Find user by contact
        User user = userRepository.findByContactValue(request.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        // Authenticate user
        AuthenticationResult result = user.authenticate(request.getContactValue(), request.getPassword(), ipAddress);

        if (!result.isSuccess()) {
            userRepository.save(user); // Save failed attempts
            throw new IllegalArgumentException(result.getReason());
        }

        // Check if 2FA is required
        if (user.isTwoFactorEnabled() && request.getTwoFactorCode() == null) {
            return AuthResponse.builder()
                .userId(user.getId().getValue().toString())
                .username(user.getUsername())
                .email(user.getPrimaryEmail())
                .role(user.getRole().name())
                .twoFactorRequired(true)
                .passwordChangeRequired(false)
                .build();
        }

        // Verify 2FA if provided
        if (user.isTwoFactorEnabled() && request.getTwoFactorCode() != null) {
            if (!verifyTwoFactorCode(user, request.getTwoFactorCode())) {
                throw new IllegalArgumentException("Invalid two-factor authentication code");
            }
        }

        // Generate tokens
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().getValue().toString());
        claims.put("role", user.getRole().name());
        claims.put("tenantId", user.getTenantId().toString());

        String accessToken = tokenProvider.generateToken(user.getUsername(), claims);
        String refreshToken = tokenProvider.generateRefreshToken(user.getUsername());

        // Create session
        sessionService.createSession(user.getId().getValue(), accessToken, refreshToken, ipAddress);

        // Save user with updated login info
        userRepository.save(user);
        
        log.info("User logged in successfully: {}", user.getUsername());

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(tokenProvider.getTokenRemainingTime(accessToken))
            .userId(user.getId().getValue().toString())
            .username(user.getUsername())
            .email(user.getPrimaryEmail())
            .role(user.getRole().name())
            .twoFactorRequired(false)
            .passwordChangeRequired(user.isPasswordMustChange())
            .expiresAt(LocalDateTime.now().plusSeconds(tokenProvider.getTokenRemainingTime(accessToken) / 1000))
            .build();
    }

    /**
     * Refreshes access token.
     */
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refreshing token");

        if (!tokenProvider.validateToken(request.getRefreshToken()) || 
            !tokenProvider.isRefreshToken(request.getRefreshToken())) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String username = tokenProvider.getUsernameFromToken(request.getRefreshToken());
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Generate new tokens
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().getValue().toString());
        claims.put("role", user.getRole().name());
        claims.put("tenantId", user.getTenantId().toString());

        String newAccessToken = tokenProvider.generateToken(username, claims);
        String newRefreshToken = tokenProvider.generateRefreshToken(username);

        // Update session
        sessionService.updateSession(request.getRefreshToken(), newAccessToken, newRefreshToken);

        return AuthResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .expiresIn(tokenProvider.getTokenRemainingTime(newAccessToken))
            .userId(user.getId().getValue().toString())
            .username(user.getUsername())
            .email(user.getPrimaryEmail())
            .role(user.getRole().name())
            .twoFactorRequired(false)
            .passwordChangeRequired(user.isPasswordMustChange())
            .expiresAt(LocalDateTime.now().plusSeconds(tokenProvider.getTokenRemainingTime(newAccessToken) / 1000))
            .build();
    }

    /**
     * Initiates password reset.
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("Password reset initiated for contact: {}", request.getContactValue());

        User user = userRepository.findByContactValue(request.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Generate reset token
        var verificationToken = user.initiateContactVerification(request.getContactValue());
        userRepository.save(user);

        // Send reset email/SMS
        if (request.getContactValue().contains("@")) {
            notificationService.sendPasswordResetEmail(request.getContactValue(), verificationToken);
        } else {
            notificationService.sendPasswordResetSms(request.getContactValue(), verificationToken);
        }

        log.info("Password reset email/SMS sent to: {}", request.getContactValue());
    }

    /**
     * Resets password with token.
     */
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Password reset with token");

        // Find user by token (this would need a separate method in repository)
        // For now, we'll implement a simplified version
        // In a real implementation, you'd need to store and validate the token

        throw new UnsupportedOperationException("Password reset with token not yet implemented");
    }

    /**
     * Changes user password.
     */
    public void changePassword(ChangePasswordRequest request) {
        log.info("Changing password for authenticated user");

        UserPrincipal currentUser = getCurrentUser();
        User user = userRepository.findById(com.fabricmanagement.identity.domain.valueobject.UserId.of(currentUser.getId()))
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.changePassword(request.getCurrentPassword(), request.getNewPassword());
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", user.getUsername());
    }

    /**
     * Verifies contact with code.
     */
    public void verifyContact(VerifyContactRequest request) {
        log.info("Verifying contact: {}", request.getContactValue());

        User user = userRepository.findByContactValue(request.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean verified = user.verifyContact(request.getContactValue(), request.getVerificationCode());
        if (!verified) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        userRepository.save(user);
        log.info("Contact verified successfully: {}", request.getContactValue());
    }

    /**
     * Logs out user.
     */
    public void logout(String refreshToken) {
        log.info("Logging out user");

        if (refreshToken != null) {
            sessionService.revokeSession(refreshToken);
        }

        SecurityContextHolder.clearContext();
        log.info("User logged out successfully");
    }

    /**
     * Gets current authenticated user.
     */
    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new IllegalArgumentException("User not authenticated");
        }
        return (UserPrincipal) authentication.getPrincipal();
    }

    /**
     * Verifies two-factor authentication code.
     */
    private boolean verifyTwoFactorCode(User user, String code) {
        // This would use a proper TOTP library in production
        // For now, we'll use a simplified implementation
        return com.fabricmanagement.identity.domain.util.TwoFactorSecret.verifyTOTP(
            user.getTwoFactorSecret(), code, 1);
    }
}