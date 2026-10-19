package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.article.FabricArticle;
import com.fabricmanagement.product.fabric.domain.article.FabricArticleSpec;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricArticleStatus;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricColourState;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "FabricArticle", description = "A fabric article with its product name")
public record FabricArticleDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String productUid,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String description,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String articleCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricArticleStatus status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int articleSpecVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricFormation formation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        FabricConstructionDto construction,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        FabricColourState colourState,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal gsmFinished,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal gsmGreige,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        BigDecimal nominalFinishedWidthCm,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal usableWidthCm,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        BigDecimal nominalGreigeWidthCm,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        BigDecimal standardRollLengthM,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String remarks,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) FabricWovenDto woven,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) FabricKnittedDto knitted,
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<FabricYarnDto> yarns,
    @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED))
        List<FabricCompositionDto> composition,
    @ArraySchema(
            arraySchema =
                @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description =
                        "Stable codes of what still stops activation: CONSTRUCTION_MISSING,"
                            + " THREAD_DENSITY_MISSING (woven ends or picks per cm),"
                            + " MACHINE_GAUGE_MISSING (knitted),"
                            + " COMPOSITION_INCOMPLETE, YARN_SHARES_INCOMPLETE"))
        List<String> activationProblems,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {

  public static FabricArticleDto from(FabricArticle article) {
    FabricArticleSpec spec = article.currentSpec();
    return new FabricArticleDto(
        article.getId(),
        article.getProductId(),
        article.getProduct().getUid(),
        article.getProduct().getName(),
        article.getProduct().getDescription(),
        spec.articleCode(),
        article.getStatus(),
        article.getArticleSpecVersion(),
        article.getFormation(),
        spec.construction() == null ? null : FabricConstructionDto.from(spec.construction()),
        spec.colourState(),
        spec.gsmFinished(),
        spec.gsmGreige(),
        spec.nominalFinishedWidthCm(),
        spec.usableWidthCm(),
        spec.nominalGreigeWidthCm(),
        spec.standardRollLengthM(),
        spec.remarks(),
        spec.woven() == null
            ? null
            : new FabricWovenDto(
                spec.woven().endsPerCm(),
                spec.woven().picksPerCm(),
                spec.woven().reedNotation(),
                spec.woven().weaveNotation()),
        spec.knitted() == null
            ? null
            : new FabricKnittedDto(
                spec.knitted().machineGauge(),
                spec.knitted().machineDiameterIn(),
                spec.knitted().coursesPerCm(),
                spec.knitted().walesPerCm(),
                spec.knitted().stitchLengthMm(),
                spec.knitted().feeders()),
        spec.yarns().stream().map(FabricYarnDto::from).toList(),
        spec.composition().stream().map(FabricCompositionDto::from).toList(),
        article.activationProblems(),
        article.getCreatedAt(),
        article.getUpdatedAt());
  }
}
