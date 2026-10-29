package com.fabricmanagement.production.core.stockunit.infra.repository;

import com.fabricmanagement.production.core.stockunit.domain.StockUnitCutPlanOperation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockUnitCutPlanOperationRepository
    extends JpaRepository<StockUnitCutPlanOperation, UUID> {
  Optional<StockUnitCutPlanOperation> findByTenantIdAndOperationId(UUID tenantId, UUID operationId);
}
