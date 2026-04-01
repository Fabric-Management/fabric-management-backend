package com.fabricmanagement.costing.app.adapter;

import com.fabricmanagement.costing.app.CostCalculationService;
import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.production.execution.workorder.app.port.ComputedCostSnapshot;
import com.fabricmanagement.production.execution.workorder.app.port.WorkOrderCostEnginePort;
import java.math.BigDecimal;
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
  public ComputedCostSnapshot computeActualCost(
      UUID tenantId,
      UUID workOrderId,
      String moduleType,
      UUID materialId,
      BigDecimal actualQuantityKg,
      UUID tradingPartnerId) {

    log.info("Computing actual cost for WorkOrder: {} in module: {}", workOrderId, moduleType);

    CostCalculation calculation =
        costCalculationService.computeActualForWorkOrder(
            tenantId, workOrderId, moduleType, materialId, actualQuantityKg, tradingPartnerId);

    return new ComputedCostSnapshot(
        workOrderId, calculation.getTotalCost(), calculation.getCurrency());
  }
}
