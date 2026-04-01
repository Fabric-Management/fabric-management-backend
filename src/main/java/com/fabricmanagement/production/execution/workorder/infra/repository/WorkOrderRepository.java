package com.fabricmanagement.production.execution.workorder.infra.repository;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkOrderRepository
    extends JpaRepository<WorkOrder, UUID>, JpaSpecificationExecutor<WorkOrder> {
  Optional<WorkOrder> findByWorkOrderNumberAndIsActiveTrue(String workOrderNumber);

  List<WorkOrder> findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID tenantId, UUID salesOrderLineId);

  /**
   * @deprecated Sprint 9: Replaced by JpaSpecificationExecutor with WorkOrderSpecification
   */
  @Deprecated(since = "Sprint 9", forRemoval = true)
  Page<WorkOrder> findByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

  /**
   * @deprecated Sprint 9: Replaced by JpaSpecificationExecutor with WorkOrderSpecification
   */
  @Deprecated(since = "Sprint 9", forRemoval = true)
  Page<WorkOrder> findByTenantIdAndStatusAndIsActiveTrue(
      UUID tenantId, WorkOrderStatus status, Pageable pageable);
}
