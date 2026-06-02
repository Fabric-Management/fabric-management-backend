package com.fabricmanagement.production.execution.workorder.app.adapter;

import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.sales.salesorder.domain.port.DraftProductionOrderCommand;
import com.fabricmanagement.sales.salesorder.domain.port.ProductionOrderPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkOrderCreationAdapter implements ProductionOrderPort {
  private final WorkOrderService workOrderService;

  @Override
  public UUID requestDraftProductionOrder(DraftProductionOrderCommand cmd) {
    CreateWorkOrderRequest request =
        CreateWorkOrderRequest.builder()
            .recipeId(cmd.recipeId())
            .tradingPartnerId(cmd.tradingPartnerId())
            .salesOrderLineId(cmd.salesOrderLineId())
            .plannedQty(cmd.plannedQty())
            .unit(cmd.unit())
            .currency(cmd.currency())
            .deadline(cmd.deadline())
            .certificationReq(cmd.certificationReq())
            .originReq(cmd.originReq())
            .build();
    return workOrderService.createWorkOrder(request).id();
  }
}
