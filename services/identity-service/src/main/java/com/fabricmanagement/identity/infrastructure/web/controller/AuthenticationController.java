package com.fabricmanagement.identity.infrastructure.web.controller;

import com.fabricmanagement.identity.application.dto.auth.*;
import com.fabricmanagement.identity.application.service.AuthenticationService;
import com.fabricmanagement.identity.infrastructure.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for authentication operations.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account and sends verification email")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "409", description = "User already exists")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for username: {}", request.getUsername());
        
        AuthResponse response = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticates user with email/phone and password")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "400", description = "Invalid credentials"),
        @ApiResponse(responseCode = "423", description = "Account locked")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        log.info("Login request for contact: {}", request.getContactValue());
        
        String ipAddress = getClientIpAddress(httpRequest);
        AuthResponse response = authenticationService.login(request, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Logs out the current user and revokes tokens")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logout successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, String>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        log.info("Logout request");
        
        String refreshToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Extract refresh token from header or request body
            // This is a simplified implementation
        }
        
        authenticationService.logout(refreshToken);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token", description = "Generates new access token using refresh token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid refresh token")
    })
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Refresh token request");
        
        AuthResponse response = authenticationService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Initiates password reset process")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password reset email/SMS sent"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Forgot password request for contact: {}", request.getContactValue());
        
        authenticationService.forgotPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password reset instructions sent"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Resets password using verification token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password reset successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid token")
    })
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Reset password request");
        
        authenticationService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password", description = "Changes user password (requires authentication)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password changed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid current password"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        log.info("Change password request");
        
        authenticationService.changePassword(request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @PostMapping("/verify-contact")
    @Operation(summary = "Verify contact", description = "Verifies email or phone with verification code")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Contact verified successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid verification code")
    })
    public ResponseEntity<Map<String, String>> verifyContact(@Valid @RequestBody VerifyContactRequest request) {
        log.info("Verify contact request for: {}", request.getContactValue());
        
        authenticationService.verifyContact(request);
        return ResponseEntity.ok(Map.of("message", "Contact verified successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns current authenticated user information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User information retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        log.info("Get current user request");
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        Map<String, Object> userInfo = Map.of(
            "id", userPrincipal.getId(),
            "username", userPrincipal.getUsername(),
            "email", userPrincipal.getEmail(),
            "role", userPrincipal.getAuthorities().iterator().next().getAuthority(),
            "twoFactorEnabled", userPrincipal.isTwoFactorEnabled()
        );
        
        return ResponseEntity.ok(userInfo);
    }

    /**
     * Gets client IP address from request.
     */
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