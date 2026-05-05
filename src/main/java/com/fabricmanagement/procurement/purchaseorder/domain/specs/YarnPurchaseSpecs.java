package com.fabricmanagement.procurement.purchaseorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Yarn-specific purchase order specifications")
public record YarnPurchaseSpecs(
    @Schema(description = "Yarn count (Ne)", example = "30/1") String yarnCount,
    @Schema(description = "Twist direction (S/Z)", example = "Z") String twist,
    @Schema(description = "Shrinkage test result") Boolean shrinkageTestPassed)
    implements PurchaseOrderSpecs {}
