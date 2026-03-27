package com.fabricmanagement.platform.auth.infra.repository;

import com.fabricmanagement.platform.auth.domain.VerificationCode;
import com.fabricmanagement.platform.auth.domain.VerificationType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
