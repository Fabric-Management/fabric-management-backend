package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.SalesQuantityPolicyOperation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesQuantityPolicyOperationRepository
    extends JpaRepository<SalesQuantityPolicyOperation, UUID> {
  Optional<SalesQuantityPolicyOperation> findByTenantIdAndOperationId(
      UUID tenantId, UUID operationId);
}
