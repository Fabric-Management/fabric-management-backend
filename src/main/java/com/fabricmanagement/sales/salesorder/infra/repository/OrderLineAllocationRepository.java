package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderLineAllocation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderLineAllocationRepository extends JpaRepository<OrderLineAllocation, UUID> {

  List<OrderLineAllocation> findByTenantIdAndSalesOrderId(UUID tenantId, UUID salesOrderId);

  /** What the deliveries carry of a line, in the line's unit; zero when nothing is allocated. */
  @Query(
      "select coalesce(sum(a.quantity), 0) from OrderLineAllocation a where a.tenantId = :tenantId"
          + " and a.lineId = :lineId")
  java.math.BigDecimal sumByLine(@Param("tenantId") UUID tenantId, @Param("lineId") UUID lineId);

  /**
   * Runs at once (not at flush), so a delivery's allocations can be replaced in one transaction
   * without the unique (delivery, line) key seeing the new rows before the old ones are gone.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "delete from OrderLineAllocation a where a.tenantId = :tenantId and a.deliveryId ="
          + " :deliveryId")
  int deleteByDelivery(@Param("tenantId") UUID tenantId, @Param("deliveryId") UUID deliveryId);

  /** Removes the allocations of lines that left the order. */
  @Modifying(flushAutomatically = true)
  @Query(
      "delete from OrderLineAllocation a where a.tenantId = :tenantId and a.salesOrderId ="
          + " :salesOrderId and a.lineId in :lineIds")
  int deleteByLines(
      @Param("tenantId") UUID tenantId,
      @Param("salesOrderId") UUID salesOrderId,
      @Param("lineIds") Collection<UUID> lineIds);
}
