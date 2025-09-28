package com.fabricmanagement.auth.application.service;

import com.fabricmanagement.auth.domain.model.UserSession;
import com.fabricmanagement.auth.domain.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user sessions.
 * Handles session creation, validation, and cleanup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SessionService {
    
    private final UserSessionRepository userSessionRepository;
    private final JwtTokenService jwtTokenService;
    
    /**
     * Creates a new user session.
     */
    public UserSession createSession(UUID userId, String username, String email, String role, 
                                   UUID tenantId, String ipAddress, String userAgent) {
        log.info("Creating session for user: {}", userId);
        
        // Create tokens
        String accessToken = jwtTokenService.createAccessToken(userId, username, email, role, tenantId);
        String refreshToken = jwtTokenService.createRefreshToken(userId, username);
        
        // Create session entity
        UserSession session = UserSession.builder()
            .userId(userId)
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .expiresAt(LocalDateTime.now().plusHours(1)) // 1 hour expiration
            .isActive(true)
            .build();
        
        UserSession savedSession = userSessionRepository.save(session);
        log.info("Session created successfully: {}", savedSession.getId());
        
        return savedSession;
    }
    
    /**
     * Validates a session.
     */
    @Transactional(readOnly = true)
    public boolean validateSession(String accessToken) {
        log.debug("Validating session with access token");
        
        if (!jwtTokenService.validateToken(accessToken)) {
            log.warn("Invalid access token");
            return false;
        }
        
        if (jwtTokenService.isTokenExpired(accessToken)) {
            log.warn("Access token expired");
            return false;
        }
        
        // Check if session exists and is active
        Optional<UserSession> sessionOpt = userSessionRepository.findByAccessToken(accessToken);
        if (sessionOpt.isEmpty()) {
            log.warn("Session not found for access token");
            return false;
        }
        
        UserSession session = sessionOpt.get();
        if (!session.getIsActive()) {
            log.warn("Session is not active");
            return false;
        }
        
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Session expired");
            return false;
        }
        
        return true;
    }
    
    /**
     * Refreshes a session.
     */
    public UserSession refreshSession(String refreshToken) {
        log.info("Refreshing session with refresh token");
        
        if (!jwtTokenService.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }
        
        if (jwtTokenService.isTokenExpired(refreshToken)) {
            throw new RuntimeException("Refresh token expired");
        }
        
        // Find existing session
        Optional<UserSession> sessionOpt = userSessionRepository.findByRefreshToken(refreshToken);
        if (sessionOpt.isEmpty()) {
            throw new RuntimeException("Session not found for refresh token");
        }
        
        UserSession session = sessionOpt.get();
        if (!session.getIsActive()) {
            throw new RuntimeException("Session is not active");
        }
        
        // Extract user information
        UUID userId = jwtTokenService.getUserIdFromToken(refreshToken);
        String username = jwtTokenService.getUsernameFromToken(refreshToken);
        String role = jwtTokenService.getRoleFromToken(refreshToken);
        UUID tenantId = jwtTokenService.getTenantIdFromToken(refreshToken);
        
        if (userId == null || username == null) {
            throw new RuntimeException("Invalid refresh token claims");
        }
        
        // Create new access token
        String newAccessToken = jwtTokenService.createAccessToken(userId, username, "", role, tenantId);
        
        // Update session
        session.setAccessToken(newAccessToken);
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        session.setUpdatedAt(LocalDateTime.now());
        
        UserSession savedSession = userSessionRepository.save(session);
        log.info("Session refreshed successfully: {}", savedSession.getId());
        
        return savedSession;
    }
    
    /**
     * Revokes a session.
     */
    public void revokeSession(String accessToken) {
        log.info("Revoking session with access token");
        
        Optional<UserSession> sessionOpt = userSessionRepository.findByAccessToken(accessToken);
        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            session.setIsActive(false);
            session.setRevokedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            userSessionRepository.save(session);
            log.info("Session revoked successfully: {}", session.getId());
        }
    }
    
    /**
     * Revokes all sessions for a user.
     */
    public void revokeAllUserSessions(UUID userId) {
        log.info("Revoking all sessions for user: {}", userId);
        
        List<UserSession> sessions = userSessionRepository.findByUserIdAndIsActive(userId, true);
        for (UserSession session : sessions) {
            session.setIsActive(false);
            session.setRevokedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
        }
        userSessionRepository.saveAll(sessions);
        log.info("All sessions revoked for user: {}", userId);
    }
    
    /**
     * Gets session by access token.
     */
    @Transactional(readOnly = true)
    public Optional<UserSession> getSessionByAccessToken(String accessToken) {
        log.debug("Fetching session by access token");
        return userSessionRepository.findByAccessToken(accessToken);
    }
    
    /**
     * Gets active sessions for a user.
     */
    @Transactional(readOnly = true)
    public List<UserSession> getActiveUserSessions(UUID userId) {
        log.debug("Fetching active sessions for user: {}", userId);
        return userSessionRepository.findByUserIdAndIsActive(userId, true);
    }
    
    /**
     * Cleans up expired sessions.
     */
    public void cleanupExpiredSessions() {
        log.info("Cleaning up expired sessions");
        
        List<UserSession> expiredSessions = userSessionRepository.findExpiredSessions(LocalDateTime.now());
        for (UserSession session : expiredSessions) {
            session.setIsActive(false);
            session.setUpdatedAt(LocalDateTime.now());
        }
        userSessionRepository.saveAll(expiredSessions);
        log.info("Cleaned up {} expired sessions", expiredSessions.size());
    }
}
