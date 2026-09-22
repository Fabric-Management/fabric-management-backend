package com.fabricmanagement.flowboard.decision.infra.repository;

import com.fabricmanagement.flowboard.decision.domain.DecisionSubjectProjection;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionSubjectProjectionRepository
    extends JpaRepository<DecisionSubjectProjection, UUID> {
  Optional<DecisionSubjectProjection> findByTenantIdAndCaseId(UUID tenantId, UUID caseId);

  List<DecisionSubjectProjection> findAllByTenantIdAndCaseIdIn(
      UUID tenantId, Collection<UUID> caseIds);
}
