package com.fabricmanagement.common.platform.auth.infra.repository;

import com.fabricmanagement.common.platform.auth.domain.VerificationCode;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for VerificationCode entity.
 */
@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    Optional<VerificationCode> findTopByTenantIdAndContactValueAndTypeOrderByCreatedAtDesc(
        UUID tenantId, String contactValue, VerificationType type);

    long countByTenantIdAndContactValueAndTypeAndCreatedAtAfter(
        UUID tenantId, String contactValue, VerificationType type, Instant createdAfter);

    long countByTenantIdAndTypeAndCreatedAtAfter(
        UUID tenantId, VerificationType type, Instant createdAfter);

    long countByCreatedAtAfter(Instant createdAfter);

    void deleteByTenantIdAndContactValueAndType(UUID tenantId, String contactValue, VerificationType type);

    void deleteByExpiresAtBefore(Instant expiryThreshold);
}

