package com.fabricmanagement.auth.domain.repository;

import com.fabricmanagement.auth.domain.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserSession entities.
 */
@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    
    /**
     * Finds session by access token.
     */
    Optional<UserSession> findByAccessToken(String accessToken);
    
    /**
     * Finds session by refresh token.
     */
    Optional<UserSession> findByRefreshToken(String refreshToken);
    
    /**
     * Finds active sessions by user ID.
     */
    List<UserSession> findByUserIdAndIsActive(UUID userId, Boolean isActive);
    
    /**
     * Finds sessions by user ID.
     */
    List<UserSession> findByUserId(UUID userId);
    
    /**
     * Finds expired sessions.
     */
    @Query("SELECT s FROM UserSession s WHERE s.expiresAt < :now AND s.isActive = true")
    List<UserSession> findExpiredSessions(@Param("now") LocalDateTime now);
    
    /**
     * Finds active sessions by IP address.
     */
    List<UserSession> findByIpAddressAndIsActive(String ipAddress, Boolean isActive);
    
    /**
     * Finds active sessions by user agent.
     */
    List<UserSession> findByUserAgentAndIsActive(String userAgent, Boolean isActive);
    
    /**
     * Counts active sessions by user ID.
     */
    long countByUserIdAndIsActive(UUID userId, Boolean isActive);
    
    /**
     * Deletes expired sessions.
     */
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    void deleteExpiredSessions(@Param("now") LocalDateTime now);
    
    /**
     * Deletes sessions by user ID.
     */
    void deleteByUserId(UUID userId);
    
    /**
     * Deletes sessions by access token.
     */
    void deleteByAccessToken(String accessToken);
    
    /**
     * Deletes sessions by refresh token.
     */
    void deleteByRefreshToken(String refreshToken);
}
