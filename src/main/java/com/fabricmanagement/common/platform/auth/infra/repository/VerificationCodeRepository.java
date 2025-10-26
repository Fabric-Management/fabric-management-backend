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

    Optional<VerificationCode> findByContactValueAndCodeAndType(
        String contactValue, String code, VerificationType type);

    Optional<VerificationCode> findTopByContactValueAndTypeOrderByCreatedAtDesc(
        String contactValue, VerificationType type);

    void deleteByContactValueAndType(String contactValue, VerificationType type);

    void deleteByExpiresAtBefore(Instant expiryThreshold);
}

