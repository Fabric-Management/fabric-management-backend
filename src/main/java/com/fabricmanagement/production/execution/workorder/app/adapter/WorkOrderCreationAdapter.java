package com.fabricmanagement.production.execution.workorder.app.adapter;

import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.sales.salesorder.domain.port.DraftProductionOrderCommand;
import com.fabricmanagement.sales.salesorder.domain.port.ProductionOrderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkOrderCreationAdapter implements ProductionOrderPort {
  private final WorkOrderService workOrderService;

  @Override
  public void requestDraftProductionOrder(DraftProductionOrderCommand cmd) {
    CreateWorkOrderRequest request =
        CreateWorkOrderRequest.builder()
            .recipeId(cmd.recipeId())
            .tradingPartnerId(cmd.tradingPartnerId())
            .salesOrderLineId(cmd.salesOrderLineId())
            .plannedQty(cmd.plannedQty())
            .unit(cmd.unit())
            .currency(cmd.currency())
            .deadline(cmd.deadline())
            // TODO(FAB-1025): Map cmd.certificationReq() and cmd.originReq() to
            // WorkOrderProductionSpecs when
            // WorkOrder supports them (needs schema update and UI traceability).
            .build();
    workOrderService.createWorkOrder(request);
  }
}
