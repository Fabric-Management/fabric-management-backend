package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPrice;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPriceClass;
import com.fabricmanagement.product.fabric.domain.reference.FabricFinishingProcess;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingBillingBasis;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.domain.vocabulary.GsmBasis;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService.QualityGradeReference;
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
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Which metres the price multiplies: what was sent or what came back")
        FinishingBillingBasis billingBasis,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description =
                "The quality grade the price is for; empty for every grade without a price of its"
                    + " own")
        UUID qualityGradeId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "1A")
        String qualityGradeCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String qualityGradeName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID priceClassId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "DARK")
        String priceClassCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String priceClassName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) FabricFormation formation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal gsmFrom,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal gsmTo,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) GsmBasis gsmBasis,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FinishingChargeUnit unit,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1.8500") BigDecimal unitPrice,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "GBP") String currency,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate validFrom) {

  /** {@code priceClass} and {@code grade} are null when the price is not narrowed by them. */
  public static FabricFinishingPriceDto from(
      FabricFinishingPrice price,
      FabricFinishingProcess process,
      FabricFinishingPriceClass priceClass,
      QualityGradeReference grade) {
    return new FabricFinishingPriceDto(
        price.getId(),
        price.getProcessId(),
        process.getCode(),
        process.getName(),
        process.getCategory(),
        price.getPartnerId(),
        price.getBillingBasis(),
        price.getQualityGradeId(),
        grade == null ? null : grade.code(),
        grade == null ? null : grade.name(),
        price.getPriceClassId(),
        priceClass == null ? null : priceClass.getCode(),
        priceClass == null ? null : priceClass.getName(),
        price.getFormation(),
        price.getGsmFrom(),
        price.getGsmTo(),
        price.getGsmBasis(),
        price.getUnit(),
        price.getUnitPrice(),
        price.getCurrency(),
        price.getValidFrom());
  }
}
