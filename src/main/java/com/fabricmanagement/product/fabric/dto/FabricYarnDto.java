package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.article.FabricArticleSpec;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricYarnRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "FabricYarn", description = "One yarn of a fabric and its share of the mass")
public record FabricYarnDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricYarnRole role,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID yarnProductId,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "The yarn's designation when it was linked")
        String designation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal sharePercent) {

  public static FabricYarnDto from(FabricArticleSpec.YarnLine line) {
    return new FabricYarnDto(
        line.role(), line.yarnProductId(), line.designation(), line.sharePercent());
  }
}
