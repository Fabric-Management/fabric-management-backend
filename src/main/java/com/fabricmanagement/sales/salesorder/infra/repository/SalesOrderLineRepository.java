package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, UUID> {

  Optional<SalesOrderLine> findByTenantIdAndId(UUID tenantId, UUID id);

  List<SalesOrderLine> findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
      UUID tenantId, UUID salesOrderId);

  List<SalesOrderLine> findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(UUID salesOrderId);

  List<SalesOrderLine> findByLineStatusAndIsActiveTrue(SalesOrderLineStatus lineStatus);

  /**
   * Used by RuleEngine: all PENDING lines for a specific SalesOrder that need recipe assignment.
   */
  List<SalesOrderLine> findBySalesOrderIdAndLineStatusAndIsActiveTrue(
      UUID salesOrderId, SalesOrderLineStatus lineStatus);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from SalesOrderLine l where l.tenantId=:tenantId and l.salesOrderId=:orderId and l.isActive=true order by l.id")
  List<SalesOrderLine> lockAllForOrder(
      @Param("tenantId") UUID tenantId, @Param("orderId") UUID orderId);
}
