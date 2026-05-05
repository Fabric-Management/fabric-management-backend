package com.fabricmanagement.procurement.purchaseorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Dye/finishing-specific purchase order specifications")
public record DyePurchaseSpecs(
    @Schema(description = "Pantone or custom color code", example = "Pantone 485") String colorCode,
    @Schema(description = "Lab dip reference number") String labDipRef,
    @Schema(description = "Approval document ID (uploaded file)") UUID approvalDocumentId)
    implements PurchaseOrderSpecs {}
