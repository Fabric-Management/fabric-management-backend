package com.fabricmanagement.production.execution.workorder.app;

import com.fabricmanagement.common.domain.event.production.SalesOrderLineProductionCompletedEvent;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkOrderProductionCompletionService {

  private static final List<WorkOrderStatus> CLOSED_STATUSES =
      List.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.CANCELLED);

  private final WorkOrderRepository workOrderRepository;
  private final DomainEventPublisher domainEventPublisher;

  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public void publishLineCompletedIfAllWorkOrdersCompleted(
      UUID tenantId, UUID salesOrderLineId, UUID completedByWorkOrderId) {
    TenantContext.setCurrentTenantId(tenantId);
    try {
      boolean hasOpenWorkOrder =
          workOrderRepository.existsByTenantIdAndSalesOrderLineIdAndIsActiveTrueAndStatusNotIn(
              tenantId, salesOrderLineId, CLOSED_STATUSES);

      if (hasOpenWorkOrder) {
        log.debug(
            "SalesOrderLine {} still has open WorkOrders after completion of {}",
            salesOrderLineId,
            completedByWorkOrderId);
        return;
      }

      domainEventPublisher.publish(
          new SalesOrderLineProductionCompletedEvent(
              tenantId, salesOrderLineId, completedByWorkOrderId));
    } finally {
      TenantContext.clear();
    }
  }
}
