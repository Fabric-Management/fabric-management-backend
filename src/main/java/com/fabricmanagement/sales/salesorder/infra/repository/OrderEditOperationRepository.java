package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderEditOperation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Safe-edit receipts. {@code (tenant_id, operation_id)} is unique in the database whatever the
 * order; that constraint is the last guard against two orders racing on one operation id.
 */
public interface OrderEditOperationRepository extends JpaRepository<OrderEditOperation, UUID> {

  /** Unique constraint name, matched when a racing insert loses (CEDIT-03 §4.3). */
  String OPERATION_UNIQUE_CONSTRAINT = "uq_order_edit_operation_tenant_operation";

  /**
   * The receipt of an operation, share-locked for the rest of the save: the retention cleanup skips
   * it while a repeat answers from it or a new base names it as origin.
   */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query(
      "select o from OrderEditOperation o where o.tenantId = :tenantId"
          + " and o.operationId = :operationId")
  Optional<OrderEditOperation> lockByTenantIdAndOperationId(
      @Param("tenantId") UUID tenantId, @Param("operationId") UUID operationId);

  Optional<OrderEditOperation> findByTenantIdAndOperationId(UUID tenantId, UUID operationId);

  List<OrderEditOperation> findByTenantIdAndSalesOrderIdOrderByRecordedAtAscIdAsc(
      UUID tenantId, UUID salesOrderId);
}
