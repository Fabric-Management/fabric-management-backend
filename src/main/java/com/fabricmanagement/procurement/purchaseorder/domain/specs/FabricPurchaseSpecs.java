package com.fabricmanagement.procurement.purchaseorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fabric-specific purchase order specifications")
public record FabricPurchaseSpecs(
    @Schema(description = "Weave construction", example = "2/1 twill") String construction,
    @Schema(description = "GSM (80-400)", example = "120") Integer gsm,
    @Schema(description = "Width in cm (100-320)", example = "150") Integer widthCm)
    implements PurchaseOrderSpecs {}
