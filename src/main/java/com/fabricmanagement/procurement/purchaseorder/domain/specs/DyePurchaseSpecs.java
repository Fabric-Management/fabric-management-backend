package com.fabricmanagement.procurement.purchaseorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dye/finishing-specific purchase order specifications")
public record DyePurchaseSpecs(
    @Schema(description = "Pantone or custom color code", example = "Pantone 485") String colorCode,
    @Schema(description = "Lab dip reference number") String labDipRef,
    @Schema(description = "Approval document ID (uploaded file)") UUID approvalDocumentId,
    @Schema(description = "Finish type", example = "Kalandır") String finishType,
    @Schema(description = "Application method", example = "Pad") String applicationMethod,
    @Schema(description = "CIELab L* target", example = "45.0") String lightnessTarget,
    @Schema(description = "Fastness requirements", example = "[\"Yıkama 4-5\"]")
        List<String> fastnessRequirements)
    implements PurchaseOrderSpecs {}
