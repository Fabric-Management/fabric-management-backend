package com.fabricmanagement.identity.infrastructure.web.controller;

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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Single Responsibility: Authentication endpoints only
 * Open/Closed: Can be extended without modification
 * REST controller for authentication operations
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
@Slf4j
public class AuthenticationController {

    private final AuthenticationUseCase authenticationUseCase;

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates a user and returns access token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());
        LoginResponse response = authenticationUseCase.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Refreshes access token using refresh token")
    public ResponseEntity<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request");
        RefreshTokenResponse response = authenticationUseCase.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Logs out a user by invalidating tokens")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        log.info("Logout request");
        String refreshToken = extractRefreshToken(authorization);
        authenticationUseCase.logout(refreshToken);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password", description = "Changes user password")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        log.info("Password change request");
        String userId = getCurrentUserId(); // Implementation would get from security context
        authenticationUseCase.changePassword(userId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Initiates password reset process")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Forgot password request for email: {}", request.getEmail());
        authenticationUseCase.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Resets password using reset token")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Password reset request");
        authenticationUseCase.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/two-factor/validate")
    @Operation(summary = "Validate two-factor", description = "Validates two-factor authentication code")
    public ResponseEntity<TwoFactorResponse> validateTwoFactor(@Valid @RequestBody TwoFactorRequest request) {
        log.info("Two-factor validation request");
        TwoFactorResponse response = authenticationUseCase.validateTwoFactor(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/two-factor/enable")
    @Operation(summary = "Enable two-factor", description = "Enables two-factor authentication")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<String> enableTwoFactor() {
        log.info("Two-factor enable request");
        String userId = getCurrentUserId(); // Implementation would get from security context
        String qrCode = authenticationUseCase.enableTwoFactor(userId);
        return ResponseEntity.ok(qrCode);
    }

    @PostMapping("/two-factor/disable")
    @Operation(summary = "Disable two-factor", description = "Disables two-factor authentication")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Void> disableTwoFactor() {
        log.info("Two-factor disable request");
        String userId = getCurrentUserId(); // Implementation would get from security context
        authenticationUseCase.disableTwoFactor(userId);
        return ResponseEntity.ok().build();
    }

    // Private helper methods
    private String extractRefreshToken(String authorization) {
        // Implementation would extract refresh token from authorization header
        return "refresh_token";
    }

    private String getCurrentUserId() {
        // Implementation would get current user ID from security context
        return "user123";
    }
}