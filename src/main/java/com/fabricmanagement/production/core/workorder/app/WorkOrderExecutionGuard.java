package com.fabricmanagement.production.core.workorder.app;

import com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock;
import com.fabricmanagement.production.core.workorder.api.WorkOrderSalesProductPort;
import com.fabricmanagement.production.core.workorder.domain.WorkOrder;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderHold;
import com.fabricmanagement.production.core.workorder.domain.exception.WorkOrderHoldException;
import com.fabricmanagement.production.core.workorder.infra.repository.WorkOrderHoldRepository;
import java.util.EnumSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Used inside command transactions. A requested hold already blocks new work, not history reads.
 */
@Component
@RequiredArgsConstructor
public class WorkOrderExecutionGuard {
  private final SalesOrderLineFulfilmentLock fulfilmentLock;
  private final WorkOrderHoldRepository holds;
  private final WorkOrderSalesProductPort salesProducts;

  public void lock(WorkOrder workOrder) {
    if (workOrder.getSalesOrderLineId() != null) {
      fulfilmentLock.lock(workOrder.getTenantId(), workOrder.getSalesOrderLineId());
    }
  }

  public void requireCurrentProduct(WorkOrder workOrder, UUID outputProductId) {
    lock(workOrder);
    if (workOrder.getSalesOrderLineId() != null
        && !salesProducts.matchesCurrentProduct(
            workOrder.getTenantId(), workOrder.getSalesOrderLineId(), outputProductId)) {
      throw new WorkOrderHoldException(
          "PRODUCT_CHANGED",
          "The sales line product changed; replan this work order before continuing",
          409);
    }
  }

  public void requireExecutable(WorkOrder workOrder) {
    lock(workOrder);
    var openStatuses =
        EnumSet.of(WorkOrderHold.Status.HOLD_REQUESTED, WorkOrderHold.Status.HOLD_CONFIRMED);
    boolean held =
        holds.existsByTenantIdAndWorkOrderIdAndStatusIn(
            workOrder.getTenantId(), workOrder.getId(), openStatuses);
    if (workOrder.getSalesOrderLineId() != null) {
      held |=
          holds.existsByTenantIdAndSalesOrderLineIdAndStatusIn(
              workOrder.getTenantId(), workOrder.getSalesOrderLineId(), openStatuses);
    }
    if (held) {
      throw new WorkOrderHoldException(
          "ACTIVE", "Work is held; production must resolve the hold first", 409);
    }
    if (workOrder.getOutputProductId() != null) {
      requireCurrentProduct(workOrder, workOrder.getOutputProductId());
    }
  }
}
