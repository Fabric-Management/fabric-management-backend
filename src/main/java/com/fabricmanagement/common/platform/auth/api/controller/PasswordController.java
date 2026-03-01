package com.fabricmanagement.common.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.auth.app.PasswordResetService;
import com.fabricmanagement.common.platform.auth.app.PasswordSetupService;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.PasswordResetRequest;
import com.fabricmanagement.common.platform.auth.dto.PasswordResetVerifyRequest;
import com.fabricmanagement.common.platform.auth.dto.PasswordSetupRequest;
import com.fabricmanagement.common.platform.auth.dto.UserContactInfoResponse;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Password setup (with token) and password reset (request code, verify and reset).
 *
 * <p>Base path: /api/auth
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class PasswordController {

  private final PasswordSetupService passwordSetupService;
  private final PasswordResetService passwordResetService;

  @PostMapping("/setup-password")
  public ResponseEntity<ApiResponse<LoginResponse>> setupPassword(
      @Valid @RequestBody PasswordSetupRequest request, HttpServletRequest httpRequest) {
    log.info(
        "Password setup request: token={}...",
        request.getToken().substring(0, Math.min(8, request.getToken().length())));
    String ipAddress = getClientIpAddress(httpRequest);
    LoginResponse response = passwordSetupService.setupPassword(request, ipAddress);
    return ResponseEntity.ok(
        ApiResponse.success(
            response,
            response.getNeedsOnboarding()
                ? "Welcome! Complete your profile to get started."
                : "Welcome back! You're all set."));
  }

  @GetMapping("/user/{contactValue}/masked-contacts")
  public ResponseEntity<ApiResponse<UserContactInfoResponse>> getMaskedContacts(
      @PathVariable String contactValue) {
    log.info("Getting masked contacts: contactValue={}", PiiMaskingUtil.maskEmail(contactValue));
    UserContactInfoResponse response = passwordResetService.getMaskedContacts(contactValue);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/password-reset/request")
  public ResponseEntity<ApiResponse<String>> requestPasswordReset(
      @Valid @RequestBody PasswordResetRequest request) {
    log.info(
        "Password reset request: authUserId={}, contactType={}",
        request.getAuthUserId(),
        request.getContactType());
    String message = passwordResetService.requestPasswordReset(request);
    return ResponseEntity.ok(ApiResponse.success(message));
  }

  @PostMapping("/password-reset/verify")
  public ResponseEntity<ApiResponse<LoginResponse>> verifyPasswordReset(
      @Valid @RequestBody PasswordResetVerifyRequest request, HttpServletRequest httpRequest) {
    log.info("Password reset verification: authUserId={}, code=***", request.getAuthUserId());
    String ipAddress = getClientIpAddress(httpRequest);
    String userAgent = httpRequest.getHeader("User-Agent");
    LoginResponse response =
        passwordResetService.verifyAndResetPassword(request, ipAddress, userAgent);
    return ResponseEntity.ok(
        ApiResponse.success(
            response, "Password reset successful! You have been automatically logged in."));
  }

  private String getClientIpAddress(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
