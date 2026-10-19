package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.reference.FabricFinishingProcess;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "FabricFinishingProcess", description = "A tenant's finishing process")
public record FabricFinishingProcessDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MERCERISING") String code,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Mercerising") String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Integer displayOrder,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricFinishingCategory category,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "What the process is usually charged by")
        FinishingChargeUnit chargeUnit,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "A lot that went through it has a colour of its own")
        boolean colours,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "It adds a layer the fabric's label must declare")
        boolean affectsLabel,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Provided by FabricOS; renameable, never removed")
        boolean systemDefined,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {

  public static FabricFinishingProcessDto from(FabricFinishingProcess process) {
    return new FabricFinishingProcessDto(
        process.getId(),
        process.getCode(),
        process.getName(),
        process.getDescription(),
        process.getDisplayOrder(),
        process.getCategory(),
        process.getChargeUnit(),
        process.getCategory().colours(),
        process.getCategory().affectsLabel(),
        process.isSystemDefined(),
        process.isUsable());
  }
}
