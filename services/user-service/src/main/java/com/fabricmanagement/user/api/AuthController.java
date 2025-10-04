package com.fabricmanagement.user.api;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.user.api.dto.*;
import com.fabricmanagement.user.application.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication REST Controller
 *
 * Handles user authentication operations
 * Base Path: /api/v1/users/auth (context-path + /auth)
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Check if contact exists and whether user has password
     */
    @PostMapping("/check-contact")
    public ResponseEntity<ApiResponse<CheckContactResponse>> checkContact(
            @Valid @RequestBody CheckContactRequest request) {

        log.debug("Checking contact: {}", request.getContactValue());

        CheckContactResponse response = authService.checkContact(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Setup initial password for user
     */
    @PostMapping("/setup-password")
    public ResponseEntity<ApiResponse<Void>> setupPassword(
            @Valid @RequestBody SetupPasswordRequest request) {

        log.info("Setting up password for contact: {}", request.getContactValue());

        try {
            authService.setupPassword(request);
            return ResponseEntity.ok(ApiResponse.success(null, "Password created successfully"));
        } catch (Exception e) {
            log.error("Error setting up password: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage(), "SETUP_PASSWORD_FAILED"));
        }
    }

    /**
     * Login with contact and password
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        log.info("Login attempt for contact: {}", request.getContactValue());

        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage(), "LOGIN_FAILED"));
        }
    }
}
