package com.fabricmanagement.identity.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for managing user sessions and tokens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String SESSION_PREFIX = "session:";
    private static final String REFRESH_PREFIX = "refresh:";
    private static final Duration SESSION_DURATION = Duration.ofDays(7);

    /**
     * Creates a new session.
     */
    public void createSession(UUID userId, String accessToken, String refreshToken, String ipAddress) {
        log.debug("Creating session for user: {}", userId);

        String sessionKey = SESSION_PREFIX + userId;
        String refreshKey = REFRESH_PREFIX + refreshToken;

        SessionData sessionData = SessionData.builder()
            .userId(userId)
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .ipAddress(ipAddress)
            .createdAt(System.currentTimeMillis())
            .build();

        redisTemplate.opsForValue().set(sessionKey, sessionData, SESSION_DURATION);
        redisTemplate.opsForValue().set(refreshKey, userId.toString(), SESSION_DURATION);

        log.debug("Session created successfully for user: {}", userId);
    }

    /**
     * Updates an existing session.
     */
    public void updateSession(String oldRefreshToken, String newAccessToken, String newRefreshToken) {
        log.debug("Updating session");

        String oldRefreshKey = REFRESH_PREFIX + oldRefreshToken;
        String userId = (String) redisTemplate.opsForValue().get(oldRefreshKey);

        if (userId != null) {
            // Remove old refresh token
            redisTemplate.delete(oldRefreshKey);

            // Create new session
            createSession(UUID.fromString(userId), newAccessToken, newRefreshToken, null);
        }
    }

    /**
     * Revokes a session.
     */
    public void revokeSession(String refreshToken) {
        log.debug("Revoking session");

        String refreshKey = REFRESH_PREFIX + refreshToken;
        String userId = (String) redisTemplate.opsForValue().get(refreshKey);

        if (userId != null) {
            String sessionKey = SESSION_PREFIX + userId;
            redisTemplate.delete(sessionKey);
            redisTemplate.delete(refreshKey);
            log.debug("Session revoked for user: {}", userId);
        }
    }

    /**
     * Checks if a session is valid.
     */
    public boolean isSessionValid(String refreshToken) {
        String refreshKey = REFRESH_PREFIX + refreshToken;
        return redisTemplate.hasKey(refreshKey);
    }

    /**
     * Gets session data for a user.
     */
    public SessionData getSessionData(UUID userId) {
        String sessionKey = SESSION_PREFIX + userId;
        return (SessionData) redisTemplate.opsForValue().get(sessionKey);
    }

    /**
     * Revokes all sessions for a user.
     */
    public void revokeAllUserSessions(UUID userId) {
        log.debug("Revoking all sessions for user: {}", userId);

        String sessionKey = SESSION_PREFIX + userId;
        SessionData sessionData = (SessionData) redisTemplate.opsForValue().get(sessionKey);

        if (sessionData != null) {
            String refreshKey = REFRESH_PREFIX + sessionData.getRefreshToken();
            redisTemplate.delete(sessionKey);
            redisTemplate.delete(refreshKey);
        }
    }

    /**
     * Session data model.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SessionData {
        private UUID userId;
        private String accessToken;
        private String refreshToken;
        private String ipAddress;
        private long createdAt;
    }
}