package com.fabricmanagement.procurement.purchaseorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fiber-specific purchase order specifications")
public record FiberPurchaseSpecs(
    @Schema(description = "Staple length (mm)", example = "28.5") Double stapleLength,
    @Schema(description = "Fiber grade (A/B/C)", example = "A") String grade,
    @Schema(description = "Moisture content %", example = "8.5") Double moistureContent)
    implements PurchaseOrderSpecs {}
