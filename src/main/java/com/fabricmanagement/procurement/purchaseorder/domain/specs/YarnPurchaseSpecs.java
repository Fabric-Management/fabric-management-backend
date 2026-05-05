package com.fabricmanagement.procurement.purchaseorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Yarn-specific purchase order specifications")
public record YarnPurchaseSpecs(
    @Schema(description = "Yarn count (Ne)", example = "30/1") String yarnCount,
    // TODO: Refactoring — rename "twist" to "twistDirection" (JSONB migration required)
    @Schema(description = "Twist direction (S/Z)", example = "Z") String twist,
    @Schema(description = "Shrinkage test result") Boolean shrinkageTestPassed,
    @Schema(description = "Fiber composition", example = "%100 Pamuk") String composition,
    @Schema(description = "Spinning method", example = "Ring Penye") String spinningMethod,
    @Schema(description = "Turns per inch") Double tpi,
    @Schema(description = "Count Strength Product") Double csp,
    @Schema(description = "Uster evenness U%") Double usterUPercentage,
    @Schema(description = "Certifications") List<String> certifications,
    @Schema(description = "Cone weight (kg)") Double coneWeight)
    implements PurchaseOrderSpecs {}
