package com.fabricmanagement.common.platform.auth.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.auth.app.JwtService;
import com.fabricmanagement.common.platform.auth.app.LoginService;
import com.fabricmanagement.common.platform.auth.app.LogoutService;
import com.fabricmanagement.common.platform.auth.app.RefreshTokenService;
import com.fabricmanagement.common.platform.auth.dto.LoginRequest;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.LogoutRequest;
import com.fabricmanagement.common.platform.auth.dto.RefreshTokenRequest;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    log.info("Login request: contactValue={}", PiiMaskingUtil.maskEmail(request.getContactValue()));
    String ipAddress = getClientIpAddress(httpRequest);
    LoginResponse response = loginService.login(request, ipAddress);
    return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
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

  private String getClientIpAddress(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
