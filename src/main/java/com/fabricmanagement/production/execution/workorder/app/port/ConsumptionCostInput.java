package com.fabricmanagement.production.execution.workorder.app.port;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port DTO representing one consumption record for multi-product cost calculation.
 *
 * <p>Passed from the production module to the costing engine via {@link WorkOrderCostEnginePort}
 * without leaking any production domain entities across module boundaries.
 *
 * @param productId the specific product ID consumed (denormalized from Batch)
 * @param moduleType e.g. SPINNING, WEAVING — determines which PriceList to query
 * @param consumedWeight the physical weight consumed (must be positive)
 * @param unit unit of measure (e.g. "KG")
 */
public record ConsumptionCostInput(
    UUID productId, WorkOrderModuleType moduleType, BigDecimal consumedWeight, String unit) {}
