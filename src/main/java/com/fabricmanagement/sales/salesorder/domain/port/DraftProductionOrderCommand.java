package com.fabricmanagement.sales.salesorder.domain.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DraftProductionOrderCommand(
    UUID recipeId,
    UUID tradingPartnerId,
    UUID salesOrderLineId,
    BigDecimal plannedQty,
    String unit,
    String currency,
    LocalDate deadline) {}
