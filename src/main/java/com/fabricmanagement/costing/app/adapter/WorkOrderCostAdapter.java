package com.fabricmanagement.costing.app.adapter;

import com.fabricmanagement.costing.app.CostCalculationService;
import com.fabricmanagement.costing.domain.calculation.CostCalculation;
import com.fabricmanagement.costing.domain.exception.CostTemplateNotFoundException;
import com.fabricmanagement.costing.domain.exception.PriceListNotFoundException;
import com.fabricmanagement.production.core.workorder.app.port.ComputedCostSnapshot;
import com.fabricmanagement.production.core.workorder.app.port.ConsumptionCostInput;
import com.fabricmanagement.production.core.workorder.app.port.WorkOrderCostEnginePort;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.core.workorder.domain.exception.PlannedCostConfigurationMissingException;
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
      WorkOrderModuleType outputModuleType,
      UUID outputProductId,
      BigDecimal actualOutputQty,
      UUID tradingPartnerId,
      List<ConsumptionCostInput> consumptions) {

    log.info(
        "Computing multi-product actual cost for WorkOrder: {} ({} consumption records)",
        workOrderId,
        consumptions.size());

    CostCalculation calculation =
        costCalculationService.computeActualForWorkOrderWithConsumptions(
            tenantId,
            workOrderId,
            outputModuleType.name(),
            outputProductId,
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
      WorkOrderModuleType moduleType,
      UUID outputProductId,
      BigDecimal plannedQuantity,
      UUID tradingPartnerId) {

    log.info(
        "Computing planned cost for WorkOrder: {} in module: {} (product: {})",
        workOrderId,
        moduleType,
        outputProductId);

    CostCalculation calculation;
    try {
      calculation =
          costCalculationService.computePlanned(
              tenantId,
              workOrderId,
              moduleType.name(),
              outputProductId,
              plannedQuantity,
              tradingPartnerId);
    } catch (PriceListNotFoundException | CostTemplateNotFoundException missing) {
      // Unconfigured costing is a tenant state, not a fault; production decides how to report it.
      throw new PlannedCostConfigurationMissingException(missing.getMessage());
    }

    return new ComputedCostSnapshot(
        workOrderId, calculation.getTotalCost(), calculation.getCurrency());
  }
}
