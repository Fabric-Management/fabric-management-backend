package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPrice;
import com.fabricmanagement.product.fabric.domain.reference.FabricFinishingProcess;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(
    name = "FabricFinishingPrice",
    description = "What a finishing process costs per metre or kilogram, from a date")
public record FabricFinishingPriceDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID processId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "DYEING_REACTIVE")
        String processCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Reactive dyeing")
        String processName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricFinishingCategory category,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "The firm the tariff belongs to; empty for the general price")
        UUID partnerId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FinishingChargeUnit unit,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1.8500") BigDecimal unitPrice,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "GBP") String currency,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate validFrom) {

  public static FabricFinishingPriceDto from(
      FabricFinishingPrice price, FabricFinishingProcess process) {
    return new FabricFinishingPriceDto(
        price.getId(),
        price.getProcessId(),
        process.getCode(),
        process.getName(),
        process.getCategory(),
        price.getPartnerId(),
        price.getUnit(),
        price.getUnitPrice(),
        price.getCurrency(),
        price.getValidFrom());
  }
}
