package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.article.FabricArticleSpec;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "FabricComposition", description = "One fibre of a fabric's declared composition")
public record FabricCompositionDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID fiberId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "CO") String isoCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Cotton") String fiberName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        MaterialSource materialSource,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "80") BigDecimal percentage) {

  public static FabricCompositionDto from(FabricArticleSpec.CompositionLine line) {
    return new FabricCompositionDto(
        line.fiberId(), line.isoCode(), line.fiberName(), line.materialSource(), line.percentage());
  }
}
