package com.fabricmanagement.production.core.batch.infra.repository;

import com.fabricmanagement.production.core.batch.domain.LotCompatibilityConfirmation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LotCompatibilityConfirmationRepository
    extends JpaRepository<LotCompatibilityConfirmation, UUID> {

  List<LotCompatibilityConfirmation> findByTenantIdAndRevokedAtIsNullAndIsActiveTrue(UUID tenantId);

  Optional<LotCompatibilityConfirmation> findByTenantIdAndId(UUID tenantId, UUID id);
}
