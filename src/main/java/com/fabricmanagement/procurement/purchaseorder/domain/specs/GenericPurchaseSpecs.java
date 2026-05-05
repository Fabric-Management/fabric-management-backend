package com.fabricmanagement.procurement.purchaseorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Generic purchase order specifications for unclassified items")
public record GenericPurchaseSpecs(
    @Schema(description = "Free-text description for untyped POs") String description)
    implements PurchaseOrderSpecs {}
