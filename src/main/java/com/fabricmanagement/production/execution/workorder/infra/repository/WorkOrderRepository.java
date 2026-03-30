package com.fabricmanagement.production.execution.workorder.infra.repository;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {
  Optional<WorkOrder> findByWorkOrderNumberAndIsActiveTrue(String workOrderNumber);

  List<WorkOrder> findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
      UUID tenantId, UUID salesOrderLineId);
}
