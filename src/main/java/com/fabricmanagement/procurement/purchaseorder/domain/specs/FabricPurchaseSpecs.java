package com.fabricmanagement.procurement.purchaseorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fabric-specific purchase order specifications")
public record FabricPurchaseSpecs(
    @Schema(description = "Weave/knit construction", example = "2/1 twill") String construction,
    // TODO: Refactoring — Integer to Double (JSONB migration required)
    @Schema(description = "GSM (80-400)", example = "120") Integer gsm,
    // TODO: Refactoring — rename "widthCm" to "width", Integer to Double (JSONB migration required)
    @Schema(description = "Width in cm (100-320)", example = "150") Integer widthCm,
    @Schema(description = "Fabric type") FabricConstructionType fabricType,
    @Schema(description = "Fiber composition", example = "%100 Pamuk") String composition,
    @Schema(description = "Weave/knit pattern", example = "Single Jersey") String weavePattern,
    @Schema(description = "Color name or Pantone", example = "Pantone 7621C") String color,
    @Schema(description = "Shrinkage tolerance (%)", example = "-3.5") Double shrinkage,
    @Schema(description = "Finish type", example = "Enzyme Wash") String finishType)
    implements PurchaseOrderSpecs {}
