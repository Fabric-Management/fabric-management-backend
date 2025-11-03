package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.domain.RefreshToken;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh Token Service - Handles access token refresh with token rotation.
 *
 * <h2>Token Rotation:</h2>
 * <ul>
 *   <li>Old refresh token is revoked</li>
 *   <li>New refresh token is generated</li>
 *   <li>Prevents token reuse attacks</li>
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
        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow(() -> {
                log.warn("Invalid refresh token");
                return new IllegalArgumentException("Invalid refresh token");
            });

        // Validate token
        if (!token.isValid()) {
            log.warn("Refresh token expired or revoked: tokenId={}", token.getId());
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = token.getUserId();

        // Get user
        com.fabricmanagement.common.platform.user.domain.User user = userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check user status
        if (!user.getIsActive()) {
            throw new IllegalArgumentException("User account is deactivated");
        }

        // Revoke old refresh token (token rotation)
        token.revoke();
        refreshTokenRepository.save(token);
        log.debug("Old refresh token revoked: tokenId={}", token.getId());

        // Generate new tokens
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        // Save new refresh token
        RefreshToken newRefreshTokenEntity = RefreshToken.create(
            user.getId(),
            newRefreshToken,
            Instant.now().plusMillis(refreshTokenExpiration)
        );
        // tenantId is automatically set by BaseEntity from TenantContext
        refreshTokenRepository.save(newRefreshTokenEntity);

        // Get UserDto for response
        UserDto userDto = userFacade.findById(tenantId, userId)
            .orElseThrow(() -> new IllegalArgumentException("User DTO not found"));

        log.info("✅ Access token refreshed: userId={}, uid={}", userId, userDto.getUid());

        return LoginResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .expiresIn(900L) // 15 minutes in seconds
            .user(userDto)
            .needsOnboarding(!user.hasCompletedOnboarding())
            .build();
    }
}

