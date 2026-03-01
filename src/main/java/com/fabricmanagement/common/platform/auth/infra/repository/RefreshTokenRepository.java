package com.fabricmanagement.common.platform.auth.infra.repository;

import com.fabricmanagement.common.platform.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for RefreshToken entity. */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByToken(String token);

  Optional<RefreshToken> findByUserId(UUID userId);

  List<RefreshToken> findByUserIdAndIsRevokedFalse(UUID userId);

  /** Active sessions: not revoked AND not expired. Ordered newest first. */
  List<RefreshToken> findByUserIdAndIsRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
      UUID userId, Instant now);

  /** Find a specific session belonging to a specific user (ownership guard). */
  Optional<RefreshToken> findByIdAndUserId(UUID id, UUID userId);

  void deleteByUserId(UUID userId);

  void deleteByToken(String token);

  /** Cleanup: delete tokens that are both expired and revoked, older than the given threshold. */
  @Modifying
  @Query("DELETE FROM RefreshToken t WHERE t.isRevoked = true AND t.expiresAt < :threshold")
  int deleteRevokedAndExpiredBefore(Instant threshold);
}
