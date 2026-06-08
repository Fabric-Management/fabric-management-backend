package com.fabricmanagement.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthCookieSupport;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.common.util.WebRequestUtils;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.auth.app.LoginService;
import com.fabricmanagement.platform.auth.app.LogoutService;
import com.fabricmanagement.platform.auth.app.MfaEventService;
import com.fabricmanagement.platform.auth.app.MfaSetupService;
import com.fabricmanagement.platform.auth.app.RefreshTokenService;
import com.fabricmanagement.platform.auth.dto.*;
import com.fabricmanagement.platform.auth.dto.ActiveSessionDto;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Auth session: login, logout, refresh token.
 *
 * <p>Base path: /api/auth. Registration and password endpoints are in RegistrationController and
 * PasswordController.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth", description = "Auth operations")
public class AuthController {

  private final LoginService loginService;
  private final LogoutService logoutService;
  private final RefreshTokenService refreshTokenService;
  private final JwtService jwtService;
  private final MfaSetupService mfaSetupService;
  private final MfaEventService mfaEventService;
  private final AuthCookieSupport authCookieSupport;

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    log.info("Login request: contactValue={}", PiiMaskingUtil.maskEmail(request.getContactValue()));
    String ipAddress = WebRequestUtils.getClientIpAddress(httpRequest);
    String userAgent = httpRequest.getHeader("User-Agent");
    LoginResponse response = loginService.login(request, ipAddress, userAgent);

    // Set tokens as HttpOnly cookies; remove from body (BE-02)
    if (response.getAccessToken() != null || response.getRefreshToken() != null) {
      authCookieSupport.addAuthCookies(
          httpResponse, response.getAccessToken(), response.getRefreshToken());
      response.setAccessToken(null);
      response.setRefreshToken(null);
    }

    return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
  }

  @PostMapping("/mfa/verify")
  public ResponseEntity<ApiResponse<LoginResponse>> verifyMfa(
      @Valid @RequestBody VerifyMfaRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    log.info("MFA verification request");
    String ipAddress = WebRequestUtils.getClientIpAddress(httpRequest);
    String userAgent = httpRequest.getHeader("User-Agent");
    LoginResponse response = loginService.verifyMfa(request, ipAddress, userAgent);

    // Set tokens as HttpOnly cookies; remove from body (BE-03)
    if (response.getAccessToken() != null || response.getRefreshToken() != null) {
      authCookieSupport.addAuthCookies(
          httpResponse, response.getAccessToken(), response.getRefreshToken());
      response.setAccessToken(null);
      response.setRefreshToken(null);
    }

    return ResponseEntity.ok(ApiResponse.success(response, "MFA verified and login successful"));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    log.info("Logout request");
    String refreshTokenFromCookie = getRefreshTokenFromCookie(httpRequest);
    if (StringUtils.hasText(refreshTokenFromCookie)) {
      logoutService.logoutByRefreshToken(refreshTokenFromCookie);
    }
    authCookieSupport.clearAuthCookies(httpResponse);
    return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    String refreshTokenFromCookie = getRefreshTokenFromCookie(httpRequest);

    if (!StringUtils.hasText(refreshTokenFromCookie)) {
      log.warn("Refresh token missing (no cookie)");
      authCookieSupport.clearAuthCookies(httpResponse);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(ApiResponse.error("UNAUTHORIZED", "Refresh token missing"));
    }

    try {
      LoginResponse response = refreshTokenService.refreshAccessToken(refreshTokenFromCookie);

      // Set new tokens as HttpOnly cookies; remove from body (BE-04)
      authCookieSupport.addAuthCookies(
          httpResponse, response.getAccessToken(), response.getRefreshToken());
      response.setAccessToken(null);
      response.setRefreshToken(null);

      return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    } catch (IllegalArgumentException e) {
      log.warn("Refresh token invalid or expired: {}", e.getMessage());
      authCookieSupport.clearAuthCookies(httpResponse);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(ApiResponse.error("UNAUTHORIZED", "Invalid or expired refresh token"));
    }
  }

  /** Read refresh_token from request cookies (BE-04). */
  private String getRefreshTokenFromCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    return Arrays.stream(cookies)
        .filter(cookie -> AuthCookieSupport.REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName()))
        .findFirst()
        .map(Cookie::getValue)
        .filter(StringUtils::hasText)
        .orElse(null);
  }

  // ── Session management endpoints ──────────────────────────────────────────────

  @GetMapping("/sessions")
  public ResponseEntity<ApiResponse<List<ActiveSessionDto>>> getActiveSessions() {
    UUID userId = getCurrentUserId();
    List<ActiveSessionDto> sessions = logoutService.getActiveSessions(userId, null);
    return ResponseEntity.ok(ApiResponse.success(sessions, "Active sessions retrieved"));
  }

  @DeleteMapping("/sessions/{sessionId}")
  public ResponseEntity<ApiResponse<Void>> revokeSession(@PathVariable UUID sessionId) {
    UUID userId = getCurrentUserId();
    logoutService.revokeSession(sessionId, userId);
    return ResponseEntity.ok(ApiResponse.success(null, "Session revoked successfully"));
  }

  @DeleteMapping("/sessions")
  public ResponseEntity<ApiResponse<Void>> revokeAllSessions() {
    UUID userId = getCurrentUserId();
    logoutService.logoutFromAllDevices(userId);
    return ResponseEntity.ok(ApiResponse.success(null, "All sessions revoked successfully"));
  }

  @PostMapping("/mfa/setup")
  public ResponseEntity<ApiResponse<MfaSetupResponse>> setupMfa(
      @Valid @RequestBody MfaSetupRequest request) {
    log.info("MFA setup request: mfaType={}", request.getMfaType());
    UUID userId = getCurrentUserId();
    UUID tenantId = getCurrentTenantId();
    MfaSetupResponse response = mfaSetupService.setupMfa(tenantId, userId, request.getMfaType());
    return ResponseEntity.ok(ApiResponse.success(response, "MFA setup initiated"));
  }

  @PostMapping("/mfa/confirm")
  public ResponseEntity<ApiResponse<Void>> confirmMfa(
      @Valid @RequestBody MfaConfirmRequest request) {
    log.info("MFA confirmation request");
    UUID userId = getCurrentUserId();
    UUID tenantId = getCurrentTenantId();
    mfaSetupService.confirmMfaSetup(tenantId, userId, request.getCode());
    return ResponseEntity.ok(ApiResponse.success(null, "MFA enabled successfully"));
  }

  @PostMapping("/mfa/disable")
  public ResponseEntity<ApiResponse<Void>> disableMfa() {
    log.info("MFA disable request");
    UUID userId = getCurrentUserId();
    UUID tenantId = getCurrentTenantId();
    mfaSetupService.disableMfa(tenantId, userId);
    return ResponseEntity.ok(ApiResponse.success(null, "MFA disabled successfully"));
  }

  /**
   * SSE endpoint for real-time MFA status updates (fallback notifications).
   *
   * <p>Frontend subscribes after receiving mfaRequired=true. Receives events when WhatsApp times
   * out and SMS fallback is triggered.
   */
  @GetMapping(value = "/mfa/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter mfaEvents(HttpServletRequest httpRequest) {
    String token =
        Optional.ofNullable(httpRequest.getHeader("Authorization"))
            .filter(header -> header.startsWith("Bearer "))
            .map(header -> header.substring(7))
            .or(() -> Optional.ofNullable(httpRequest.getParameter("token")))
            .filter(StringUtils::hasText)
            .orElse(null);

    if (token == null) {
      throw new PlatformDomainException("MFA token required", "AUTH_MFA_TOKEN_REQUIRED", 401);
    }

    if (!jwtService.validateToken(token)) {
      throw new PlatformDomainException(
          "Invalid or expired MFA token", "AUTH_MFA_TOKEN_INVALID", 401);
    }

    if (!jwtService.isPreAuthToken(token)) {
      throw new PlatformDomainException(
          "Only pre-auth MFA tokens can subscribe to MFA events", "AUTH_MFA_TOKEN_INVALID", 400);
    }

    UUID userId = jwtService.getUserIdFromToken(token);
    log.info("MFA SSE subscription for user: {}", userId);
    return mfaEventService.subscribe(userId);
  }

  @GetMapping("/mfa/status")
  public ResponseEntity<ApiResponse<MfaStatusResponse>> getMfaStatus() {
    log.info("MFA status request");
    UUID userId = getCurrentUserId();
    UUID tenantId = getCurrentTenantId();
    MfaStatusResponse response = mfaSetupService.getMfaStatus(tenantId, userId);
    return ResponseEntity.ok(ApiResponse.success(response, "MFA status retrieved"));
  }

  /**
   * Current user from SecurityContext (set by JwtAuthenticationFilter from cookie or Bearer
   * header). Use this for authenticated endpoints instead of reading Authorization header (cookie
   * auth does not send Bearer).
   */
  private UUID getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      throw new PlatformDomainException("Not authenticated", "AUTH_NOT_AUTHENTICATED", 401);
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof AuthenticatedUserContext ctx) {
      return ctx.userId();
    }
    if (principal instanceof String str) {
      return UUID.fromString(str);
    }
    throw new PlatformDomainException("Not authenticated", "AUTH_NOT_AUTHENTICATED", 401);
  }

  /**
   * Current tenant from SecurityContext (AuthenticatedUserContext.tenantId). Use for MFA/session
   * endpoints that need tenant scope.
   */
  private UUID getCurrentTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getDetails() instanceof AuthenticatedUserContext)) {
      throw new PlatformDomainException("Not authenticated", "AUTH_NOT_AUTHENTICATED", 401);
    }
    UUID tenantId = ((AuthenticatedUserContext) auth.getDetails()).tenantId();
    if (tenantId == null) {
      throw new PlatformDomainException(
          "Tenant context not available", "AUTH_TENANT_CONTEXT_MISSING", 401);
    }
    return tenantId;
  }
}
