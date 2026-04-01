package com.fabricmanagement.production.execution.workorder.app.port;

import java.math.BigDecimal;
import java.util.UUID;

public interface WorkOrderCostEnginePort {

  ComputedCostSnapshot computeActualCost(
      UUID tenantId,
      UUID workOrderId,
      String moduleType,
      UUID materialId,
      BigDecimal actualQuantityKg,
      UUID tradingPartnerId);
}
