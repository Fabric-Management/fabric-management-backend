package com.fabricmanagement.common.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.auth.app.RegistrationService;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.RegisterCheckRequest;
import com.fabricmanagement.common.platform.auth.dto.VerifyAndRegisterRequest;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Registration flow: check eligibility + send code, verify code + complete registration.
 *
 * <p>Base path: /api/auth/register
 */
@RestController
@RequestMapping("/api/auth/register")
@RequiredArgsConstructor
@Slf4j
public class RegistrationController {

  private final RegistrationService registrationService;

  @PostMapping("/check")
  public ResponseEntity<ApiResponse<String>> checkRegistrationEligibility(
      @Valid @RequestBody RegisterCheckRequest request) {
    log.info(
        "Registration eligibility check: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));
    String message = registrationService.checkEligibilityAndSendCode(request);
    return ResponseEntity.ok(ApiResponse.success(message));
  }

  @PostMapping("/verify")
  public ResponseEntity<ApiResponse<LoginResponse>> verifyAndRegister(
      @Valid @RequestBody VerifyAndRegisterRequest request) {
    log.info(
        "Verify and register: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));
    LoginResponse response = registrationService.verifyAndRegister(request);
    return ResponseEntity.ok(ApiResponse.success(response, "Registration completed successfully"));
  }
}
