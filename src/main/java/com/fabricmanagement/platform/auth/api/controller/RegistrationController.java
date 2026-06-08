package com.fabricmanagement.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthCookieSupport;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.common.util.WebRequestUtils;
import com.fabricmanagement.platform.auth.app.RegistrationService;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.RegisterCheckRequest;
import com.fabricmanagement.platform.auth.dto.VerifyAndRegisterRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
@RequestMapping("/api/v1/auth/register")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Registration", description = "Registration operations")
public class RegistrationController {

  private final RegistrationService registrationService;
  private final AuthCookieSupport authCookieSupport;

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
      @Valid @RequestBody VerifyAndRegisterRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    log.info(
        "Verify and register: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));
    String ipAddress = WebRequestUtils.getClientIpAddress(httpRequest);
    String userAgent = httpRequest.getHeader("User-Agent");
    LoginResponse response = registrationService.verifyAndRegister(request, ipAddress, userAgent);

    if (response.getAccessToken() != null || response.getRefreshToken() != null) {
      authCookieSupport.addAuthCookies(
          httpResponse, response.getAccessToken(), response.getRefreshToken());
      response.setAccessToken(null);
      response.setRefreshToken(null);
    }

    return ResponseEntity.ok(ApiResponse.success(response, "Registration completed successfully"));
  }
}
