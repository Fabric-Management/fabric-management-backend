package com.fabricmanagement.user.infrastructure.repository;

import com.fabricmanagement.user.domain.valueobject.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PasswordResetToken Repository Interface
 * 
 * Provides data access methods for password reset tokens
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    
    /**
     * Find token by token value
     */
    Optional<PasswordResetToken> findByToken(String token);
    
    /**
     * Find tokens by user ID
     */
    List<PasswordResetToken> findByUserId(UUID userId);
    
    /**
     * Find tokens by contact value
     */
    List<PasswordResetToken> findByContactValue(String contactValue);
    
    /**
     * Find active tokens (not expired, not used, attempts remaining)
     */
    @Query("SELECT t FROM PasswordResetToken t WHERE t.expiresAt > :now AND t.isUsed = false AND t.attemptsRemaining > 0")
    List<PasswordResetToken> findActiveTokens(@Param("now") LocalDateTime now);
    
    /**
     * Find expired tokens
     */
    @Query("SELECT t FROM PasswordResetToken t WHERE t.expiresAt <= :now")
    List<PasswordResetToken> findExpiredTokens(@Param("now") LocalDateTime now);
    
    /**
     * Find used tokens
     */
    List<PasswordResetToken> findByIsUsedTrue();
    
    /**
     * Find tokens by reset method
     */
    List<PasswordResetToken> findByResetMethod(PasswordResetToken.ResetMethod resetMethod);
    
    /**
     * Count active tokens by user
     */
    @Query("SELECT COUNT(t) FROM PasswordResetToken t WHERE t.userId = :userId AND t.expiresAt > :now AND t.isUsed = false")
    long countActiveTokensByUser(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
    
    /**
     * Delete expired tokens
     */
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt <= :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
    
    /**
     * Find tokens created after specific time
     */
    List<PasswordResetToken> findByCreatedAtAfter(LocalDateTime createdAt);
}
