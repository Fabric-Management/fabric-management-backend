package com.fabricmanagement.production.core.stockunit.infra.repository;

import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockUnitCutPlanRepository extends JpaRepository<StockUnitCutPlan, UUID> {
  Optional<StockUnitCutPlan> findByTenantIdAndId(UUID tenantId, UUID id);

  List<StockUnitCutPlan> findByTenantIdAndTargetLineIdOrderByCreatedAtDescIdDesc(
      UUID tenantId, UUID lineId);

  List<StockUnitCutPlan> findByTenantIdAndTargetTypeAndTargetId(
      UUID tenantId, String type, UUID documentId);
}
