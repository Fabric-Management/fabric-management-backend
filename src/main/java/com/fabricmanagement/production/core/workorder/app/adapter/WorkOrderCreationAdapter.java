package com.fabricmanagement.production.core.workorder.app.adapter;

import com.fabricmanagement.production.core.workorder.app.WorkOrderService;
import com.fabricmanagement.production.core.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.sales.salesorder.domain.port.DraftProductionOrderCommand;
import com.fabricmanagement.sales.salesorder.domain.port.ProductionOrderPort;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkOrderCreationAdapter implements ProductionOrderPort {
  private final WorkOrderService workOrderService;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  @Autowired
  public WorkOrderCreationAdapter(
      WorkOrderService workOrderService, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.workOrderService = workOrderService;
    this.objectMapper = objectMapper;
  }

  public WorkOrderCreationAdapter(WorkOrderService workOrderService) {
    this.workOrderService = workOrderService;
    this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
  }

  @Override
  public UUID requestDraftProductionOrder(DraftProductionOrderCommand cmd) {
    CreateWorkOrderRequest request =
        CreateWorkOrderRequest.builder()
            .salesOrderId(cmd.salesOrderId())
            .productCode(cmd.productCode())
            .recipeId(cmd.recipeId())
            .outputProductId(cmd.outputProductId())
            .tradingPartnerId(cmd.tradingPartnerId())
            .salesOrderLineId(cmd.salesOrderLineId())
            .requirementProfileId(cmd.requirementProfileId())
            .requirementProfileVersion(cmd.requirementProfileVersion())
            .requirementProfileSnapshot(
                cmd.requirementProfileSnapshot() == null
                    ? null
                    : objectMapper.valueToTree(cmd.requirementProfileSnapshot()))
            .plannedQty(cmd.plannedQty())
            .unit(cmd.unit())
            .currency(cmd.currency())
            .deadline(cmd.deadline())
            .certificationReq(cmd.certificationReq())
            .originReq(cmd.originReq())
            .build();
    return workOrderService.createWorkOrder(request).id();
  }

  @Override
  public boolean hasActiveProduction(UUID tenantId, UUID salesOrderLineId) {
    return workOrderService.hasActiveProduction(tenantId, salesOrderLineId);
  }
}
