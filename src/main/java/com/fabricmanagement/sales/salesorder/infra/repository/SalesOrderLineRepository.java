package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, UUID> {

  Optional<SalesOrderLine> findByTenantIdAndId(UUID tenantId, UUID id);

  List<SalesOrderLine> findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(UUID salesOrderId);

  List<SalesOrderLine> findByLineStatusAndIsActiveTrue(SalesOrderLineStatus lineStatus);

  /**
   * Used by RuleEngine: all PENDING lines for a specific SalesOrder that need recipe assignment.
   */
  List<SalesOrderLine> findBySalesOrderIdAndLineStatusAndIsActiveTrue(
      UUID salesOrderId, SalesOrderLineStatus lineStatus);
}
