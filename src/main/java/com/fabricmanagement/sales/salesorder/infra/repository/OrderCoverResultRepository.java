package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderCoverResult;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCoverResultRepository extends JpaRepository<OrderCoverResult, UUID> {
  Optional<OrderCoverResult> findByTenantIdAndSalesOrderIdAndId(
      UUID tenantId, UUID orderId, UUID id);

  Optional<OrderCoverResult> findByTenantIdAndCaseIdAndId(UUID tenantId, UUID caseId, UUID id);

  List<OrderCoverResult> findAllByTenantIdAndCaseIdOrderByRecordedAtAsc(UUID tenantId, UUID caseId);
}
