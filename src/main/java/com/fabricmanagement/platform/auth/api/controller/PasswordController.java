package com.fabricmanagement.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.logging.SensitiveLogData;
import com.fabricmanagement.common.infrastructure.security.AuthCookieSupport;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.rate.RateLimited;
import com.fabricmanagement.common.util.WebRequestUtils;
import com.fabricmanagement.platform.auth.app.PasswordResetService;
import com.fabricmanagement.platform.auth.app.PasswordSetupService;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.PasswordResetRequest;
import com.fabricmanagement.platform.auth.dto.PasswordResetVerifyRequest;
import com.fabricmanagement.platform.auth.dto.PasswordSetupRequest;
import com.fabricmanagement.platform.auth.dto.SetupLinkCheckRequest;
import com.fabricmanagement.platform.auth.dto.SetupLinkStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Password setup (with token) and password reset (request code, verify and reset).
 *
 * <p>Base path: /api/v1/auth
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Password", description = "Password operations")
public class PasswordController {

  private final PasswordSetupService passwordSetupService;
  private final PasswordResetService passwordResetService;
  private final AuthCookieSupport authCookieSupport;

  @Operation(
      summary = "Use a setup link",
      description =
          "Uses an emailed setup link. A self-service signup link builds the company, admin user"
              + " and trial in one step; a sales-led or invitation link sets up the user. When the"
              + " address has no login yet a password (min. 8 characters) is required and the"
              + " response signs the user in with auth cookies. When the address already has a"
              + " login, the password is omitted, the existing one is kept, signInRequired is true"
              + " and no cookies are set: the user signs in normally.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Link used; either signed in (cookies set) or signInRequired = true"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description =
            "AUTH_TOKEN_INVALID (unknown, used or expired link), AUTH_PASSWORD_REQUIRED"
                + " (no login yet and no password given) or TAX_ID_ALREADY_EXISTS (the tax ID"
                + " was registered after the form was sent)"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "AUTH_PASSWORD_ALREADY_SET (the link's user already has a password)"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "Validation failed (e.g. password shorter than 8 characters)"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "429",
        description = "Too many requests from this address")
  })
  @PostMapping("/setup-password")
  @RateLimited(requests = 10, windowSeconds = 60)
  @SensitiveLogData
  public ResponseEntity<ApiResponse<LoginResponse>> setupPassword(
      @Valid @RequestBody PasswordSetupRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    log.info("Password setup requested");
    String ipAddress = WebRequestUtils.getClientIpAddress(httpRequest);
    LoginResponse response = passwordSetupService.setupPassword(request, ipAddress);

    if (response.getAccessToken() != null || response.getRefreshToken() != null) {
      authCookieSupport.addAuthCookies(
          httpResponse, response.getAccessToken(), response.getRefreshToken());
      response.setAccessToken(null);
      response.setRefreshToken(null);
    }

    String message =
        Boolean.TRUE.equals(response.getSignInRequired())
            ? "Your company has been added to your account. Sign in to continue."
            : response.getNeedsOnboarding()
                ? "Welcome! Complete your profile to get started."
                : "Welcome back! You're all set.";
    return ResponseEntity.ok(ApiResponse.success(response, message));
  }

  /**
   * Lets the setup page decide what to show before the person types anything: a usable link with or
   * without a password field, or "this link no longer works". Does not use the link; the token is
   * in the body so it does not end up in access logs.
   */
  @Operation(
      summary = "Check a setup link",
      description =
          "Tells the setup page whether the link can still be used and whether it must ask for a"
              + " password. Does not use the link. Unknown, used and expired links all answer"
              + " valid = false.")
  @ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Link status"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "422",
        description = "Token missing"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "429",
        description = "Too many requests from this address")
  })
  @PostMapping("/setup-link/check")
  @RateLimited(requests = 20, windowSeconds = 60)
  @SensitiveLogData
  public ResponseEntity<ApiResponse<SetupLinkStatus>> checkSetupLink(
      @Valid @RequestBody SetupLinkCheckRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(passwordSetupService.checkLink(request.getToken())));
  }

  /** Always answers the same way, whether or not an account uses the address. */
  @PostMapping("/password-reset/request")
  public ResponseEntity<ApiResponse<String>> requestPasswordReset(
      @Valid @RequestBody PasswordResetRequest request) {
    String message = passwordResetService.requestPasswordReset(request);
    return ResponseEntity.ok(ApiResponse.success(message));
  }

  /**
   * Sets the new password. The client signs in afterwards with the new password through the normal
   * login, so MFA and organization selection still apply.
   */
  @PostMapping("/password-reset/verify")
  public ResponseEntity<ApiResponse<Void>> verifyPasswordReset(
      @Valid @RequestBody PasswordResetVerifyRequest request) {
    passwordResetService.resetPassword(request);
    return ResponseEntity.ok(ApiResponse.success(null, "Password has been reset."));
  }
}
