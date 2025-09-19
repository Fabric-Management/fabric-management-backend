package com.fabricmanagement.identity.infrastructure.web.controller;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import com.fabricmanagement.identity.application.dto.auth.*;
import com.fabricmanagement.identity.application.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations.
 * Provides unified authentication flow with contact verification.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
@Slf4j
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    /**
     * Initiates authentication with any contact (email or phone).
     * This is the entry point for all authentication attempts.
     */
    @PostMapping("/initiate")
    @Operation(summary = "Initiate authentication",
              description = "Start authentication process with email or phone number")
    public ResponseEntity<ApiResponse<AuthInitiationResponse>> initiateAuth(
        @Valid @RequestBody AuthInitiationRequest request
    ) {
        log.info("Authentication initiated for contact: {}", request.getContact());
        AuthInitiationResponse response = authenticationService.initiateAuthentication(request);

        return ResponseEntity.ok(ApiResponse.success(response,
            determineMessage(response)));
    }

    /**
     * Verifies contact and optionally enables password creation.
     */
    @PostMapping("/verify")
    @Operation(summary = "Verify contact",
              description = "Verify email/phone with code and optionally create password")
    public ResponseEntity<ApiResponse<VerificationResponse>> verifyContact(
        @Valid @RequestBody VerificationRequest request
    ) {
        log.info("Verifying contact: {}", request.getContact());
        VerificationResponse response = authenticationService.verifyContact(request);

        return ResponseEntity.ok(ApiResponse.success(response,
            "Contact verified successfully"));
    }

    /**
     * Creates initial password for new users.
     */
    @PostMapping("/create-password")
    @Operation(summary = "Create password",
              description = "Create initial password after contact verification")
    public ResponseEntity<ApiResponse<PasswordCreationResponse>> createPassword(
        @Valid @RequestBody PasswordCreationRequest request
    ) {
        log.info("Creating password for new user");
        PasswordCreationResponse response = authenticationService.createPassword(request);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response,
                "Password created successfully. Welcome!"));
    }

    /**
     * Standard login with contact and password.
     */
    @PostMapping("/login")
    @Operation(summary = "Login",
              description = "Authenticate with contact (email/phone) and password")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        log.info("Login attempt for contact: {}", request.getContact());

        // Add IP address to request
        request.setIpAddress(getClientIpAddress(httpRequest));

        LoginResponse response = authenticationService.login(request);

        return ResponseEntity.ok(ApiResponse.success(response,
            "Login successful"));
    }

    /**
     * Verifies two-factor authentication code.
     */
    @PostMapping("/verify-2fa")
    @Operation(summary = "Verify 2FA",
              description = "Complete login with two-factor authentication code")
    public ResponseEntity<ApiResponse<TwoFactorResponse>> verifyTwoFactor(
        @Valid @RequestBody TwoFactorRequest request
    ) {
        log.info("Verifying 2FA code");
        TwoFactorResponse response = authenticationService.verifyTwoFactor(request);

        return ResponseEntity.ok(ApiResponse.success(response,
            "Two-factor authentication successful"));
    }

    /**
     * Refreshes access token.
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token",
              description = "Get new access token using refresh token")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
        @Valid @RequestBody RefreshTokenRequest request
    ) {
        log.debug("Refreshing access token");
        RefreshTokenResponse response = authenticationService.refreshToken(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Logs out user.
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout",
              description = "Invalidate current session")
    public ResponseEntity<ApiResponse<Void>> logout(
        @RequestHeader("Authorization") String authHeader
    ) {
        log.info("User logout");
        String token = extractToken(authHeader);
        authenticationService.logout(token);

        return ResponseEntity.ok(ApiResponse.success(null,
            "Logout successful"));
    }

    /**
     * Initiates password reset.
     */
    @PostMapping("/password-reset/initiate")
    @Operation(summary = "Initiate password reset",
              description = "Start password reset process")
    public ResponseEntity<ApiResponse<PasswordResetResponse>> initiatePasswordReset(
        @Valid @RequestBody PasswordResetRequest request
    ) {
        log.info("Password reset initiated for: {}", request.getContact());
        PasswordResetResponse response = authenticationService.initiatePasswordReset(request);

        return ResponseEntity.ok(ApiResponse.success(response,
            "Password reset instructions sent"));
    }

    /**
     * Completes password reset.
     */
    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Reset password",
              description = "Complete password reset with verification code")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
        @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        log.info("Completing password reset");
        authenticationService.resetPassword(request);

        return ResponseEntity.ok(ApiResponse.success(null,
            "Password reset successful. You can now login with your new password."));
    }

    // Helper methods

    private String determineMessage(AuthInitiationResponse response) {
        switch (response.getNextStep()) {
            case "VERIFY_CONTACT":
                return "Verification code sent to " + response.getMaskedContact();
            case "CREATE_PASSWORD":
                return "Please create your password";
            case "ENTER_PASSWORD":
                return "Please enter your password";
            case "NOT_FOUND":
                return "Authentication initiated";
            default:
                return "Authentication initiated";
        }
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header");
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}