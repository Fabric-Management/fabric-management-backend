package com.fabricmanagement.platform.auth.infra.repository;

import com.fabricmanagement.platform.auth.domain.VerificationCode;
import com.fabricmanagement.platform.auth.domain.VerificationType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for VerificationCode entity. */
@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

  Optional<VerificationCode> findTopByTenantIdAndContactValueAndTypeOrderByCreatedAtDesc(
      UUID tenantId, String contactValue, VerificationType type);

  long countByTenantIdAndContactValueAndTypeAndCreatedAtAfter(
      UUID tenantId, String contactValue, VerificationType type, Instant createdAfter);

  long countByTenantIdAndTypeAndCreatedAtAfter(
      UUID tenantId, VerificationType type, Instant createdAfter);

  long countByCreatedAtAfter(Instant createdAfter);

  void deleteByTenantIdAndContactValueAndType(
      UUID tenantId, String contactValue, VerificationType type);

  void deleteByExpiresAtBefore(Instant expiryThreshold);

  /**
   * Adds one failed attempt directly in the database. Deliberately leaves {@code version} alone: a
   * concurrent request that is validating the same code must not fail with an optimistic-lock error
   * because of a wrong attempt recorded next to it.
   */
  @Modifying
  @Query("UPDATE VerificationCode c SET c.attemptCount = c.attemptCount + 1 WHERE c.id = :id")
  int incrementAttemptCount(@Param("id") UUID id);
}
