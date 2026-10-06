package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderEditBase;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Edit bases of one tenant; RLS isolates tenants and every lookup names the tenant too. */
public interface OrderEditBaseRepository extends JpaRepository<OrderEditBase, UUID> {

  /**
   * The base a save names, only on its own order and by its own actor; anything else is unknown and
   * reveals nothing. Read with a share lock so the retention cleanup, which skips locked rows,
   * cannot delete it while the save that uses it is still open.
   */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query(
      "select b from OrderEditBase b where b.tenantId = :tenantId and b.id = :id"
          + " and b.salesOrderId = :orderId and b.actorId = :actorId")
  Optional<OrderEditBase> findForUse(
      @Param("tenantId") UUID tenantId,
      @Param("id") UUID id,
      @Param("orderId") UUID orderId,
      @Param("actorId") UUID actorId);

  List<OrderEditBase> findByTenantIdAndSalesOrderIdOrderByCapturedAtAscIdAsc(
      UUID tenantId, UUID salesOrderId);
}
