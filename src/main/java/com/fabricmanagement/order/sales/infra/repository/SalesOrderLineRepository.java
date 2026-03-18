package com.fabricmanagement.order.sales.infra.repository;

import com.fabricmanagement.order.sales.domain.SalesOrderLine;
import com.fabricmanagement.order.sales.domain.SalesOrderLineStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, UUID> {

  List<SalesOrderLine> findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(UUID salesOrderId);

  List<SalesOrderLine> findByLineStatusAndIsActiveTrue(SalesOrderLineStatus lineStatus);

  /**
   * Used by RuleEngine: all PENDING lines for a specific SalesOrder that need recipe assignment.
   */
  List<SalesOrderLine> findBySalesOrderIdAndLineStatusAndIsActiveTrue(
      UUID salesOrderId, SalesOrderLineStatus lineStatus);
}
