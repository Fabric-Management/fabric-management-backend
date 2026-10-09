package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
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

  /** Active lines of several orders at once, so a page of orders loads its totals in one query. */
  List<SalesOrderLine> findByTenantIdAndSalesOrderIdInAndIsActiveTrue(
      UUID tenantId, Collection<UUID> salesOrderIds);

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

  /**
   * Ids of the order's active lines, read without loading or locking the line rows (CEDIT-07-F1). A
   * save calls it after it holds the order row, which every writer that removes a line of the order
   * takes first, so the answer stays true until that save ends.
   */
  @Query(
      "select l.id from SalesOrderLine l where l.tenantId=:tenantId and l.salesOrderId=:orderId and l.isActive=true")
  List<UUID> findActiveLineIds(@Param("tenantId") UUID tenantId, @Param("orderId") UUID orderId);

  /**
   * The line a safe-edit add created from this client id, active or removed (CEDIT-03): a client id
   * adds at most one line to an order, ever.
   */
  Optional<SalesOrderLine> findByTenantIdAndSalesOrderIdAndClientLineId(
      UUID tenantId, UUID salesOrderId, UUID clientLineId);
}
