package com.fabricmanagement.production.execution.output.infra.repository;

import com.fabricmanagement.production.execution.output.domain.ProductionOutputRecord;
import com.fabricmanagement.production.execution.output.domain.ProductionOutputStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionOutputRecordRepository
    extends JpaRepository<ProductionOutputRecord, UUID> {
  Optional<ProductionOutputRecord> findByTenantIdAndWorkOrderIdAndStatusAndIsActiveTrue(
      UUID tenantId, UUID workOrderId, ProductionOutputStatus status);

  List<ProductionOutputRecord> findByTenantIdAndBatchIdAndIsActiveTrue(UUID tenantId, UUID batchId);

  List<ProductionOutputRecord> findByTenantIdAndWorkOrderIdAndIsActiveTrue(
      UUID tenantId, UUID workOrderId);

  Optional<ProductionOutputRecord> findByIdAndTenantIdAndIsActiveTrue(UUID id, UUID tenantId);
}
