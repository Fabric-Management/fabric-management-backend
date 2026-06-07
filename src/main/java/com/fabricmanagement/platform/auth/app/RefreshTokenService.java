package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token Service - Handles access token refresh with token rotation.
 *
 * <h2>Token Rotation:</h2>
 *
 * <ul>
 *   <li>Old refresh token is revoked
 *   <li>New refresh token is generated
 *   <li>Prevents token reuse attacks
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final UserFacade userFacade;
  private final JwtService jwtService;

  @Value("${application.jwt.expiration:900000}")
  private long accessTokenExpiration;

  @Value("${application.jwt.refresh-expiration:604800000}")
  private long refreshTokenExpiration;

  /**
   * Refresh access token using refresh token.
   *
   * @param refreshToken The refresh token
   * @return New LoginResponse with fresh tokens
   */
  @Transactional
  public LoginResponse refreshAccessToken(String refreshToken) {
    log.info("Refresh token request");

    // Find refresh token
    RefreshToken token =
        refreshTokenRepository
            .findByToken(refreshToken)
            .orElseThrow(
                () -> {
                  log.warn("Invalid refresh token");
                  return new IllegalArgumentException("Invalid refresh token");
                });

    // Validate token
    if (!token.isValid()) {
      log.warn("Refresh token expired or revoked: tokenId={}", token.getId());
      throw new PlatformDomainException(
          "Refresh token expired or revoked", "AUTH_REFRESH_TOKEN_INVALID", 401);
    }

    UUID tenantId = TenantContext.requireTenantId();
    UUID userId = token.getUserId();

    // Get user
    com.fabricmanagement.platform.user.domain.User user =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // Check user status
    if (!user.getIsActive()) {
      throw new PlatformDomainException(
          "User account is deactivated", "AUTH_USER_DEACTIVATED", 403);
    }

    // Revoke old refresh token (token rotation)
    token.revoke();
    refreshTokenRepository.save(token);
    log.debug("Old refresh token revoked: tokenId={}", token.getId());

    // Generate new tokens
    String newAccessToken = jwtService.generateAccessToken(user);
    String newRefreshToken = jwtService.generateRefreshToken(user);

    // Carry over device info from old token (same session, same device)
    RefreshToken newRefreshTokenEntity =
        RefreshToken.create(
            user.getId(),
            newRefreshToken,
            Instant.now().plusMillis(refreshTokenExpiration),
            token.getIpAddress(),
            token.getUserAgent(),
            token.getDeviceName());
    refreshTokenRepository.save(newRefreshTokenEntity);

    // Get UserDto for response
    UserDto userDto =
        userFacade
            .findById(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User DTO not found"));

    log.info("✅ Access token refreshed: userId={}, uid={}", userId, userDto.getUid());

    return LoginResponse.builder()
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken)
        .expiresIn(accessTokenExpiration / 1000)
        .user(userDto)
        .needsOnboarding(!user.hasCompletedOnboarding())
        .build();
  }
}
