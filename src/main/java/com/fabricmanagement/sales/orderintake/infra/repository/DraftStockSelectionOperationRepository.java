package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.DraftStockSelectionOperation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftStockSelectionOperationRepository
    extends JpaRepository<DraftStockSelectionOperation, UUID> {
  Optional<DraftStockSelectionOperation> findByTenantIdAndOperationId(
      UUID tenantId, UUID operationId);
}
