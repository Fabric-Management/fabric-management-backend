package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.domain.event.UserLogoutEvent;
import com.fabricmanagement.common.platform.auth.infra.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Logout Service - Handles user logout and token revocation.
 *
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Revoke refresh token</li>
 *   <li>Publish logout event</li>
 *   <li>Support "logout from all devices" (optional)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogoutService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * Logout user by revoking refresh token.
     *
     * @param refreshToken The refresh token to revoke
     * @param userId User ID from security context
     */
    @Transactional
    public void logout(String refreshToken, UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Logout request: tenantId={}, userId={}", tenantId, userId);

        // Find and revoke refresh token
        refreshTokenRepository.findByToken(refreshToken)
            .ifPresentOrElse(
                token -> {
                    if (!token.getUserId().equals(userId)) {
                        log.warn("Token user mismatch: tokenUserId={}, requestUserId={}",
                            token.getUserId(), userId);
                        throw new IllegalArgumentException("Invalid token for user");
                    }

                    token.revoke();
                    refreshTokenRepository.save(token);
                    log.info("✅ Refresh token revoked: tokenId={}", token.getId());
                },
                () -> {
                    log.warn("Refresh token not found: token={}***", 
                        refreshToken.substring(0, Math.min(8, refreshToken.length())));
                    // Continue anyway - token might already be revoked or expired
                }
            );

        // Publish logout event
        eventPublisher.publish(new UserLogoutEvent(tenantId, userId));

        log.info("✅ Logout successful: userId={}", userId);
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

        refreshTokenRepository.findByUserIdAndIsRevokedFalse(userId)
            .forEach(token -> {
                token.revoke();
                refreshTokenRepository.save(token);
                log.debug("Revoked token: tokenId={}", token.getId());
            });

        eventPublisher.publish(new UserLogoutEvent(tenantId, userId));

        log.info("✅ Logged out from all devices: userId={}", userId);
    }
}

