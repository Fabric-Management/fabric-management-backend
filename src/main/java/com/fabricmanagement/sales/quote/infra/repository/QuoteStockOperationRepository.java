package com.fabricmanagement.sales.quote.infra.repository;

import com.fabricmanagement.sales.quote.domain.QuoteStockOperation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteStockOperationRepository extends JpaRepository<QuoteStockOperation, UUID> {
  Optional<QuoteStockOperation> findByTenantIdAndOperationId(UUID tenantId, UUID operationId);
}
