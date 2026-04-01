package com.fabricmanagement.production.execution.workorder.infra.repository;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
  Optional<WorkOrder> findByWorkOrderNumberAndIsActiveTrue(String workOrderNumber);

  List<WorkOrder> findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID tenantId, UUID salesOrderLineId);

  /** Paginated listing — all active WorkOrders for tenant. */
  Page<WorkOrder> findByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

  /** Paginated listing — filtered by status. */
  Page<WorkOrder> findByTenantIdAndStatusAndIsActiveTrue(
      UUID tenantId, WorkOrderStatus status, Pageable pageable);
}
