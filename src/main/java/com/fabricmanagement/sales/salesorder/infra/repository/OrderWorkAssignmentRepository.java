package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderWorkAssignment;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderWorkAssignmentRepository extends JpaRepository<OrderWorkAssignment, UUID> {

  Optional<OrderWorkAssignment> findByTenantIdAndSalesOrderIdAndKind(
      UUID tenantId, UUID salesOrderId, OrderWorkKind kind);

  /** Serialises claims and assignments of the same work: only one of two claims succeeds. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select a from OrderWorkAssignment a where a.tenantId = :tenantId"
          + " and a.salesOrderId = :orderId and a.kind = :kind")
  Optional<OrderWorkAssignment> lockByOrderAndKind(
      @Param("tenantId") UUID tenantId,
      @Param("orderId") UUID orderId,
      @Param("kind") OrderWorkKind kind);

  List<OrderWorkAssignment> findByTenantIdAndKindOrderByRoutedAtAsc(
      UUID tenantId, OrderWorkKind kind);

  List<OrderWorkAssignment> findByTenantIdAndKindAndSalesOrderIdIn(
      UUID tenantId, OrderWorkKind kind, Collection<UUID> salesOrderIds);
}
