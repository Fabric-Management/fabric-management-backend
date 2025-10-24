package com.fabricmanagement.common.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.auth.app.LoginService;
import com.fabricmanagement.common.platform.auth.app.RegistrationService;
import com.fabricmanagement.common.platform.auth.dto.LoginRequest;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.RegisterCheckRequest;
import com.fabricmanagement.common.platform.auth.dto.VerifyAndRegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Auth Controller - Authentication endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/auth/register/check - Check eligibility & send verification</li>
 *   <li>POST /api/auth/register/verify - Verify code & complete registration</li>
 *   <li>POST /api/auth/login - Login with credentials</li>
 *   <li>POST /api/auth/logout - Logout (revoke tokens)</li>
 *   <li>POST /api/auth/refresh - Refresh access token</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final LoginService loginService;
    private final RegistrationService registrationService;

    @PostMapping("/register/check")
    public ResponseEntity<ApiResponse<String>> checkRegistrationEligibility(
            @Valid @RequestBody RegisterCheckRequest request) {
        log.info("Registration eligibility check: contactValue={}", request.getContactValue());

        String message = registrationService.checkEligibilityAndSendCode(request);

        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyAndRegister(
            @Valid @RequestBody VerifyAndRegisterRequest request) {
        log.info("Verify and register: contactValue={}", request.getContactValue());

        LoginResponse response = registrationService.verifyAndRegister(request);

        return ResponseEntity.ok(ApiResponse.success(response, "Registration completed successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        log.info("Login request: contactValue={}", request.getContactValue());

        String ipAddress = getClientIpAddress(httpRequest);
        LoginResponse response = loginService.login(request, ipAddress);

        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

