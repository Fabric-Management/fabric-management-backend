package com.fabricmanagement.production.execution.workorder.app.port;

import java.math.BigDecimal;
import java.util.UUID;

public record ComputedCostSnapshot(UUID workOrderId, BigDecimal totalActualCost, String currency) {}
