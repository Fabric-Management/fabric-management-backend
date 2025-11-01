package com.fabricmanagement.common.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.auth.app.LoginService;
import com.fabricmanagement.common.platform.auth.app.PasswordResetService;
import com.fabricmanagement.common.platform.auth.app.PasswordSetupService;
import com.fabricmanagement.common.platform.auth.app.RegistrationService;
import com.fabricmanagement.common.platform.auth.dto.LoginRequest;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.PasswordResetRequest;
import com.fabricmanagement.common.platform.auth.dto.PasswordResetVerifyRequest;
import com.fabricmanagement.common.platform.auth.dto.PasswordSetupRequest;
import com.fabricmanagement.common.platform.auth.dto.RegisterCheckRequest;
import com.fabricmanagement.common.platform.auth.dto.UserContactInfoResponse;
import com.fabricmanagement.common.platform.auth.dto.VerifyAndRegisterRequest;
import com.fabricmanagement.common.util.PiiMaskingUtil;
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
 *   <li>GET /api/auth/user/{contactValue}/masked-contacts - Get masked contacts for password reset</li>
 *   <li>POST /api/auth/password-reset/request - Request password reset (send verification code)</li>
 *   <li>POST /api/auth/password-reset/verify - Verify code & reset password (complete flow)</li>
 *   <li>POST /api/auth/setup-password - Setup password with token</li>
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
    private final PasswordSetupService passwordSetupService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register/check")
    public ResponseEntity<ApiResponse<String>> checkRegistrationEligibility(
            @Valid @RequestBody RegisterCheckRequest request) {
        log.info("Registration eligibility check: contactValue={}", 
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        String message = registrationService.checkEligibilityAndSendCode(request);

        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyAndRegister(
            @Valid @RequestBody VerifyAndRegisterRequest request) {
        log.info("Verify and register: contactValue={}", 
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        LoginResponse response = registrationService.verifyAndRegister(request);

        return ResponseEntity.ok(ApiResponse.success(response, "Registration completed successfully"));
    }

    @PostMapping("/setup-password")
    public ResponseEntity<ApiResponse<LoginResponse>> setupPassword(
            @Valid @RequestBody PasswordSetupRequest request) {
        
        log.info("Password setup request: token={}...", 
            request.getToken().substring(0, 8));

        LoginResponse response = passwordSetupService.setupPassword(request);

        log.info("✅ Password setup successful, needsOnboarding={}", 
            response.getNeedsOnboarding());

        return ResponseEntity.ok(ApiResponse.success(
            response,
            response.getNeedsOnboarding()
                ? "Welcome! Complete your profile to get started."
                : "Welcome back! You're all set."
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        log.info("Login request: contactValue={}", 
            PiiMaskingUtil.maskEmail(request.getContactValue()));

        String ipAddress = getClientIpAddress(httpRequest);
        LoginResponse response = loginService.login(request, ipAddress);

        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @GetMapping("/user/{contactValue}/masked-contacts")
    public ResponseEntity<ApiResponse<UserContactInfoResponse>> getMaskedContacts(
            @PathVariable String contactValue) {
        log.info("Getting masked contacts: contactValue={}",
            PiiMaskingUtil.maskEmail(contactValue));

        UserContactInfoResponse response = passwordResetService.getMaskedContacts(contactValue);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<String>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        log.info("Password reset request: authUserId={}, contactType={}",
            request.getAuthUserId(),
            request.getContactType());

        String message = passwordResetService.requestPasswordReset(request);

        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @PostMapping("/password-reset/verify")
    public ResponseEntity<ApiResponse<LoginResponse>> verifyPasswordReset(
            @Valid @RequestBody PasswordResetVerifyRequest request) {
        log.info("Password reset verification: authUserId={}, code={}***",
            request.getAuthUserId(),
            request.getCode().substring(0, Math.min(2, request.getCode().length())));

        LoginResponse response = passwordResetService.verifyAndResetPassword(request);

        log.info("✅ Password reset completed successfully: userId={}, needsOnboarding={}",
            response.getUser().getId(),
            response.getNeedsOnboarding());

        return ResponseEntity.ok(ApiResponse.success(
            response,
            "Password reset successful! You have been automatically logged in."
        ));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

