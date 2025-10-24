package com.fabricmanagement.auth.infrastructure.repository;

import com.fabricmanagement.auth.domain.aggregate.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh Token Repository
 * 
 * Repository for RefreshToken aggregate
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    
    /**
     * Find token by hash
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    
    /**
     * Find active tokens by user ID
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.userId = :userId AND rt.isRevoked = false AND rt.expiresAt > :now")
    List<RefreshToken> findActiveTokensByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
    
    /**
     * Find tokens by user ID and tenant
     */
    List<RefreshToken> findByUserIdAndTenantId(UUID userId, UUID tenantId);
    
    /**
     * Revoke all tokens for user
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.userId = :userId")
    void revokeAllTokensForUser(@Param("userId") UUID userId);
    
    /**
     * Revoke all tokens for user in tenant
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.userId = :userId AND rt.tenantId = :tenantId")
    void revokeAllTokensForUserInTenant(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
    
    /**
     * Delete expired tokens
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
    
    /**
     * Count active tokens for user
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.userId = :userId AND rt.isRevoked = false AND rt.expiresAt > :now")
    long countActiveTokensForUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
    
    /**
     * Find tokens by user ID
     */
    List<RefreshToken> findByUserId(UUID userId);
    
    /**
     * Find tokens by tenant ID
     */
    List<RefreshToken> findByTenantId(UUID tenantId);
    
    /**
     * Check if token exists by hash
     */
    boolean existsByTokenHash(String tokenHash);
    
    /**
     * Find active tokens by user ID (not revoked)
     */
    List<RefreshToken> findByUserIdAndIsRevokedFalse(UUID userId);
    
    /**
     * Check if user has active tokens
     */
    boolean existsByUserIdAndIsRevokedFalse(UUID userId);
    
    /**
     * Delete token by hash
     */
    void deleteByTokenHash(String tokenHash);
    
    /**
     * Delete all tokens by user ID
     */
    void deleteByUserId(UUID userId);
    
    /**
     * Revoke all tokens by user ID
     */
    void revokeAllByUserId(UUID userId);
}
