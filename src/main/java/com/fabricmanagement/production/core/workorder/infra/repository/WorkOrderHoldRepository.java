package com.fabricmanagement.production.core.workorder.infra.repository;

import com.fabricmanagement.production.core.workorder.domain.WorkOrderHold;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkOrderHoldRepository extends JpaRepository<WorkOrderHold, UUID> {

  List<WorkOrderHold> findByTenantIdAndSalesOrderLineIdOrderByRequestedAtDescIdDesc(
      UUID tenantId, UUID salesOrderLineId);

  List<WorkOrderHold> findByTenantIdAndStatusInOrderByRequestedAtAscIdAsc(
      UUID tenantId, Collection<WorkOrderHold.Status> statuses);

  boolean existsByTenantIdAndSalesOrderLineIdAndStatusIn(
      UUID tenantId, UUID lineId, Collection<WorkOrderHold.Status> statuses);

  boolean existsByTenantIdAndWorkOrderIdAndStatusIn(
      UUID tenantId, UUID workOrderId, Collection<WorkOrderHold.Status> statuses);

  Optional<WorkOrderHold> findByTenantIdAndId(UUID tenantId, UUID id);
}
