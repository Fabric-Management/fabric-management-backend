package com.fabricmanagement.common.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.auth.app.JwtService;
import com.fabricmanagement.common.platform.auth.app.LoginService;
import com.fabricmanagement.common.platform.auth.app.LogoutService;
import com.fabricmanagement.common.platform.auth.app.MfaEventService;
import com.fabricmanagement.common.platform.auth.app.MfaSetupService;
import com.fabricmanagement.common.platform.auth.app.RefreshTokenService;
import com.fabricmanagement.common.platform.auth.dto.*;
import com.fabricmanagement.common.platform.auth.dto.ActiveSessionDto;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Auth session: login, logout, refresh token.
 *
 * <p>Base path: /api/auth. Registration and password endpoints are in RegistrationController and
 * PasswordController.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

  private final LoginService loginService;
  private final LogoutService logoutService;
  private final RefreshTokenService refreshTokenService;
  private final JwtService jwtService;
  private final MfaSetupService mfaSetupService;
  private final MfaEventService mfaEventService;

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    log.info("Login request: contactValue={}", PiiMaskingUtil.maskEmail(request.getContactValue()));
    String ipAddress = getClientIpAddress(httpRequest);
    String userAgent = httpRequest.getHeader("User-Agent");
    LoginResponse response = loginService.login(request, ipAddress, userAgent);
    return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
  }

  @PostMapping("/mfa/verify")
  public ResponseEntity<ApiResponse<LoginResponse>> verifyMfa(
      @Valid @RequestBody VerifyMfaRequest request, HttpServletRequest httpRequest) {
    log.info("MFA verification request");
    String ipAddress = getClientIpAddress(httpRequest);
    String userAgent = httpRequest.getHeader("User-Agent");
    LoginResponse response = loginService.verifyMfa(request, ipAddress, userAgent);
    return ResponseEntity.ok(ApiResponse.success(response, "MFA verified and login successful"));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(
      @Valid @RequestBody LogoutRequest request, HttpServletRequest httpRequest) {
    log.info("Logout request");
    UUID userId = extractUserIdFromRequest(httpRequest);
    logoutService.logout(request.getRefreshToken(), userId);
    return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
  }

  @PostMapping("/refresh")
  public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
      @Valid @RequestBody RefreshTokenRequest request) {
    log.info("Refresh token request");
    LoginResponse response = refreshTokenService.refreshAccessToken(request.getRefreshToken());
    return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
  }

  // ── Session management endpoints ──────────────────────────────────────────────

  @GetMapping("/sessions")
  public ResponseEntity<ApiResponse<List<ActiveSessionDto>>> getActiveSessions(
      HttpServletRequest httpRequest) {
    UUID userId = extractUserIdFromRequest(httpRequest);
    List<ActiveSessionDto> sessions = logoutService.getActiveSessions(userId, null);
    return ResponseEntity.ok(ApiResponse.success(sessions, "Active sessions retrieved"));
  }

  @DeleteMapping("/sessions/{sessionId}")
  public ResponseEntity<ApiResponse<Void>> revokeSession(
      @PathVariable UUID sessionId, HttpServletRequest httpRequest) {
    UUID userId = extractUserIdFromRequest(httpRequest);
    logoutService.revokeSession(sessionId, userId);
    return ResponseEntity.ok(ApiResponse.success(null, "Session revoked successfully"));
  }

  @DeleteMapping("/sessions")
  public ResponseEntity<ApiResponse<Void>> revokeAllSessions(HttpServletRequest httpRequest) {
    UUID userId = extractUserIdFromRequest(httpRequest);
    logoutService.logoutFromAllDevices(userId);
    return ResponseEntity.ok(ApiResponse.success(null, "All sessions revoked successfully"));
  }

  @PostMapping("/mfa/setup")
  public ResponseEntity<ApiResponse<MfaSetupResponse>> setupMfa(
      @Valid @RequestBody MfaSetupRequest request, HttpServletRequest httpRequest) {
    log.info("MFA setup request: mfaType={}", request.getMfaType());
    UUID userId = extractUserIdFromRequest(httpRequest);
    UUID tenantId = extractTenantIdFromRequest(httpRequest);
    MfaSetupResponse response = mfaSetupService.setupMfa(tenantId, userId, request.getMfaType());
    return ResponseEntity.ok(ApiResponse.success(response, "MFA setup initiated"));
  }

  @PostMapping("/mfa/confirm")
  public ResponseEntity<ApiResponse<Void>> confirmMfa(
      @Valid @RequestBody MfaConfirmRequest request, HttpServletRequest httpRequest) {
    log.info("MFA confirmation request");
    UUID userId = extractUserIdFromRequest(httpRequest);
    UUID tenantId = extractTenantIdFromRequest(httpRequest);
    mfaSetupService.confirmMfaSetup(tenantId, userId, request.getCode());
    return ResponseEntity.ok(ApiResponse.success(null, "MFA enabled successfully"));
  }

  @PostMapping("/mfa/disable")
  public ResponseEntity<ApiResponse<Void>> disableMfa(HttpServletRequest httpRequest) {
    log.info("MFA disable request");
    UUID userId = extractUserIdFromRequest(httpRequest);
    UUID tenantId = extractTenantIdFromRequest(httpRequest);
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
    String token = null;

    String authHeader = httpRequest.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      token = authHeader.substring(7);
    } else {
      token = httpRequest.getParameter("token");
    }

    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("MFA token required");
    }

    if (!jwtService.validateToken(token)) {
      throw new IllegalArgumentException("Invalid or expired MFA token");
    }

    if (!jwtService.isPreAuthToken(token)) {
      throw new IllegalArgumentException("Only pre-auth MFA tokens can subscribe to MFA events");
    }

    UUID userId = jwtService.getUserIdFromToken(token);
    log.info("MFA SSE subscription for user: {}", userId);
    return mfaEventService.subscribe(userId);
  }

  @GetMapping("/mfa/status")
  public ResponseEntity<ApiResponse<MfaStatusResponse>> getMfaStatus(
      HttpServletRequest httpRequest) {
    log.info("MFA status request");
    UUID userId = extractUserIdFromRequest(httpRequest);
    UUID tenantId = extractTenantIdFromRequest(httpRequest);
    MfaStatusResponse response = mfaSetupService.getMfaStatus(tenantId, userId);
    return ResponseEntity.ok(ApiResponse.success(response, "MFA status retrieved"));
  }

  private UUID extractUserIdFromRequest(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new IllegalArgumentException("Authorization header missing or invalid");
    }
    String token = authHeader.substring(7);
    try {
      return jwtService.getUserIdFromToken(token);
    } catch (Exception e) {
      log.warn("Failed to extract userId from token: {}", e.getMessage());
      throw new IllegalArgumentException("Invalid token");
    }
  }

  private UUID extractTenantIdFromRequest(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new IllegalArgumentException("Authorization header missing or invalid");
    }
    String token = authHeader.substring(7);
    try {
      return jwtService.getTenantIdFromToken(token);
    } catch (Exception e) {
      log.warn("Failed to extract tenantId from token: {}", e.getMessage());
      throw new IllegalArgumentException("Invalid token");
    }
  }

  private String getClientIpAddress(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
