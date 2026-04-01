package com.fabricmanagement.production.execution.workorder.app.adapter;

import com.fabricmanagement.costing.app.port.WorkOrderPlanningUpdatePort;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production-module adapter for {@link WorkOrderPlanningUpdatePort}.
 *
 * <p>Registered as @Component (not @Service) per ArchUnit Rule 6.2: adapters implementing
 * cross-module ports must not be @Service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkOrderPlannedCostAdapter implements WorkOrderPlanningUpdatePort {

  private final WorkOrderRepository workOrderRepository;

  @Override
  @Transactional
  public void updatePlannedCost(
      UUID tenantId, UUID workOrderId, BigDecimal plannedCost, String currency) {

    WorkOrder wo =
        workOrderRepository
            .findById(workOrderId)
            .filter(w -> w.getTenantId().equals(tenantId))
            .filter(WorkOrder::getIsActive)
            .orElseThrow(() -> new EntityNotFoundException("WorkOrder not found: " + workOrderId));

    wo.updatePlannedCost(plannedCost, currency);
    workOrderRepository.save(wo);

    log.info("PlannedCost written to WorkOrder {}: {} {}", workOrderId, plannedCost, currency);
  }
}
