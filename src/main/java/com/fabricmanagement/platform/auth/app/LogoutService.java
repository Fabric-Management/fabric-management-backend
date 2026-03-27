package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.domain.event.UserLogoutEvent;
import com.fabricmanagement.platform.auth.dto.ActiveSessionDto;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logout and session management: single-device logout, revoke all, list active sessions, revoke by
 * session ID.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogoutService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final DomainEventPublisher eventPublisher;

  /**
   * Logout user by revoking refresh token (requires authenticated context for userId).
   *
   * @param refreshToken The refresh token to revoke
   * @param userId User ID from security context
   */
  @Transactional
  public void logout(String refreshToken, UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Logout request: tenantId={}, userId={}", tenantId, userId);

    // Find and revoke refresh token
    refreshTokenRepository
        .findByToken(refreshToken)
        .ifPresentOrElse(
            token -> {
              if (!token.getUserId().equals(userId)) {
                log.warn(
                    "Token user mismatch: tokenUserId={}, requestUserId={}",
                    token.getUserId(),
                    userId);
                throw new IllegalArgumentException("Invalid token for user");
              }

              token.revoke();
              refreshTokenRepository.save(token);
              log.info("✅ Refresh token revoked: tokenId={}", token.getId());
            },
            () -> {
              log.warn(
                  "Refresh token not found: token={}***",
                  refreshToken.substring(0, Math.min(8, refreshToken.length())));
              // Continue anyway - token might already be revoked or expired
            });

    // Publish logout event
    eventPublisher.publish(new UserLogoutEvent(tenantId, userId));

    log.info("✅ Logout successful: userId={}", userId);
  }

  /**
   * Logout by refresh token only (e.g. when request has only refresh cookie, no access token).
   * Finds the token, revokes it, and publishes logout event using tenantId/userId from the token.
   *
   * @param refreshToken The refresh token from cookie
   */
  @Transactional
  public void logoutByRefreshToken(String refreshToken) {
    log.info("Logout by refresh token (cookie)");
    refreshTokenRepository
        .findByToken(refreshToken)
        .ifPresentOrElse(
            token -> {
              UUID tenantId = token.getTenantId();
              UUID userId = token.getUserId();
              token.revoke();
              refreshTokenRepository.save(token);
              eventPublisher.publish(new UserLogoutEvent(tenantId, userId));
              log.info(
                  "✅ Refresh token revoked (cookie logout): tokenId={}, userId={}",
                  token.getId(),
                  userId);
            },
            () ->
                log.warn(
                    "Refresh token not found on logout: token={}***",
                    refreshToken != null && !refreshToken.isEmpty()
                        ? refreshToken.substring(0, Math.min(8, refreshToken.length()))
                        : ""));
  }

  /**
   * Logout user from all devices by revoking all refresh tokens.
   *
   * @param userId User ID
   */
  @Transactional
  public void logoutFromAllDevices(UUID userId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Logout from all devices: tenantId={}, userId={}", tenantId, userId);

    refreshTokenRepository
        .findByUserIdAndIsRevokedFalse(userId)
        .forEach(
            token -> {
              token.revoke();
              refreshTokenRepository.save(token);
              log.debug("Revoked token: tokenId={}", token.getId());
            });

    eventPublisher.publish(new UserLogoutEvent(tenantId, userId));

    log.info("✅ Logged out from all devices: userId={}", userId);
  }

  /**
   * List active (non-revoked, non-expired) sessions for a user.
   *
   * @param userId user whose sessions to list
   * @param currentTokenId the RefreshToken.id of the requester's current session (for isCurrent
   *     flag)
   * @return list of active sessions, newest first
   */
  @Transactional(readOnly = true)
  public List<ActiveSessionDto> getActiveSessions(UUID userId, UUID currentTokenId) {
    List<RefreshToken> tokens =
        refreshTokenRepository.findByUserIdAndIsRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            userId, Instant.now());

    return tokens.stream()
        .map(t -> ActiveSessionDto.from(t, t.getId().equals(currentTokenId)))
        .toList();
  }

  /**
   * Revoke a specific session by its ID.
   *
   * @param sessionId RefreshToken PK
   * @param userId ownership guard — must belong to this user
   */
  @Transactional
  public void revokeSession(UUID sessionId, UUID userId) {
    RefreshToken token =
        refreshTokenRepository
            .findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

    if (token.getIsRevoked()) {
      log.info("Session already revoked: sessionId={}", sessionId);
      return;
    }

    token.revoke();
    refreshTokenRepository.save(token);
    log.info("Session revoked: sessionId={}, userId={}", sessionId, userId);
  }
}
