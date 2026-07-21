package com.fabricmanagement.production.quality.decision.infra.repository;

import com.fabricmanagement.production.quality.decision.domain.QualityDecisionUnit;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionUnitId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QualityDecisionUnitRepository
    extends JpaRepository<QualityDecisionUnit, QualityDecisionUnitId> {

  long countByTenantIdAndDecisionId(UUID tenantId, UUID decisionId);
}
