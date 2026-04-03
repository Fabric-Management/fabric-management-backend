package com.fabricmanagement.costing.app.adapter;

import com.fabricmanagement.costing.app.CostCalculationService;
import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.production.execution.workorder.app.port.ComputedCostSnapshot;
import com.fabricmanagement.production.execution.workorder.app.port.ConsumptionCostInput;
import com.fabricmanagement.production.execution.workorder.app.port.WorkOrderCostEnginePort;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderCostAdapter implements WorkOrderCostEnginePort {

  private final CostCalculationService costCalculationService;

  @Override
  public ComputedCostSnapshot computeActualCostFromConsumptions(
      UUID tenantId,
      UUID workOrderId,
      String outputModuleType,
      UUID outputMaterialId,
      BigDecimal actualOutputQty,
      UUID tradingPartnerId,
      List<ConsumptionCostInput> consumptions) {

    log.info(
        "Computing multi-material actual cost for WorkOrder: {} ({} consumption records)",
        workOrderId,
        consumptions.size());

    CostCalculation calculation =
        costCalculationService.computeActualForWorkOrderWithConsumptions(
            tenantId,
            workOrderId,
            outputModuleType,
            outputMaterialId,
            actualOutputQty,
            tradingPartnerId,
            consumptions);

    return new ComputedCostSnapshot(
        workOrderId, calculation.getTotalCost(), calculation.getCurrency());
  }

  @Override
  public ComputedCostSnapshot computePlannedCost(
      UUID tenantId,
      UUID workOrderId,
      String moduleType,
      UUID outputMaterialId,
      BigDecimal plannedQuantity,
      UUID tradingPartnerId) {

    log.info(
        "Computing planned cost for WorkOrder: {} in module: {} (material: {})",
        workOrderId,
        moduleType,
        outputMaterialId);

    CostCalculation calculation =
        costCalculationService.computePlanned(
            tenantId, workOrderId, moduleType, outputMaterialId, plannedQuantity, tradingPartnerId);

    return new ComputedCostSnapshot(
        workOrderId, calculation.getTotalCost(), calculation.getCurrency());
  }
}
