package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.yarn.domain.article.ComponentKind;
import com.fabricmanagement.product.yarn.domain.article.LayerRole;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleComposition;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStructureComponent;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleTwistStage;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountBasis;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountSystem;
import com.fabricmanagement.product.yarn.domain.vocabulary.FilamentForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistDirection;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistStageType;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnConstructionFeature;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnMaterialForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnStructureType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Schema(description = "Full tenant-scoped yarn article view")
public record YarnArticleDto(
    UUID id,
    String uid,
    UUID productId,
    YarnArticleStatus status,
    int articleSpecVersion,
    CountSystem originalCountSystem,
    BigDecimal originalCountValue,
    CountBasis countBasis,
    YarnStructureType structureType,
    Integer foldCount,
    Integer filamentCount,
    BigDecimal twistContractionPercent,
    BigDecimal resultantLinearDensityTex,
    String canonicalDesignation,
    String sourceDesignation,
    YarnMaterialForm materialForm,
    SpinningTechnologyFamily spinningTechnologyFamily,
    SpinningSystemRef spinningSystem,
    FilamentForm filamentForm,
    String name,
    String description,
    String canonicalKey,
    short canonicalKeyAlgorithmVersion,
    List<YarnConstructionFeature> constructionFeatures,
    List<CompositionRow> composition,
    List<StructureComponentRow> structureComponents,
    List<TwistStageRow> twistStages,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy) {

  public static YarnArticleDto from(YarnArticle article) {
    return new YarnArticleDto(
        article.getId(),
        article.getUid(),
        article.getProductId(),
        article.getStatus(),
        article.getArticleSpecVersion(),
        article.getOriginalCountSystem(),
        article.getOriginalCountValue(),
        article.getCountBasis(),
        article.getStructureType(),
        article.getFoldCount(),
        article.getFilamentCount(),
        article.getTwistContractionPercent(),
        article.getResultantLinearDensityTex(),
        article.getCanonicalDesignation(),
        article.getSourceDesignation(),
        article.getMaterialForm(),
        article.getSpinningTechnologyFamily(),
        article.getSpinningSystemRef() == null
            ? null
            : new SpinningSystemRef(
                article.getSpinningSystemRef().getId(),
                article.getSpinningSystemRef().getCode(),
                article.getSpinningSystemRef().getName()),
        article.getFilamentForm(),
        article.getName(),
        article.getDescription(),
        article.getCanonicalKey(),
        article.getCanonicalKeyAlgorithmVersion(),
        article.getConstructionFeatures().stream().map(row -> row.getFeature()).sorted().toList(),
        article.getComposition().stream()
            .sorted(Comparator.comparing(row -> row.getFiberId().toString()))
            .map(CompositionRow::from)
            .toList(),
        article.getStructureComponents().stream().map(StructureComponentRow::from).toList(),
        article.getTwistStages().stream().map(TwistStageRow::from).toList(),
        article.getCreatedAt(),
        article.getCreatedBy(),
        article.getUpdatedAt(),
        article.getUpdatedBy());
  }

  @Schema(name = "YarnArticleSpinningSystemRef")
  public record SpinningSystemRef(UUID id, String code, String name) {}

  @Schema(name = "YarnArticleCompositionRow")
  public record CompositionRow(
      UUID fiberId,
      String fiberIsoCode,
      String fiberName,
      MaterialSource materialSource,
      BigDecimal percentage) {

    static CompositionRow from(YarnArticleComposition row) {
      return new CompositionRow(
          row.getFiberId(),
          row.getFiberIsoCode(),
          row.getFiberName(),
          row.getMaterialSource(),
          row.getPercentage());
    }
  }

  @Schema(name = "YarnArticleStructureComponentRow")
  public record StructureComponentRow(
      ComponentKind kind,
      int componentIndex,
      LayerRole layerRole,
      CountSystem componentCountSystem,
      BigDecimal componentCountValue,
      BigDecimal componentLinearDensityTex,
      UUID fiberId,
      String fiberIsoCode,
      String fiberName,
      MaterialSource materialSource,
      String label) {

    static StructureComponentRow from(YarnArticleStructureComponent row) {
      return new StructureComponentRow(
          row.getKind(),
          row.getComponentIndex(),
          row.getLayerRole(),
          row.getComponentCountSystem(),
          row.getComponentCountValue(),
          row.getComponentLinearDensityTex(),
          row.getFiberId(),
          row.getFiberIsoCode(),
          row.getFiberName(),
          row.getMaterialSource(),
          row.getLabel());
    }
  }

  @Schema(name = "YarnArticleTwistStageRow")
  public record TwistStageRow(
      TwistStageType stageType,
      int sequence,
      TwistDirection direction,
      BigDecimal turnsPerMeter,
      Integer strandComponentIndex,
      UUID testMethodId,
      String testMethodCode) {

    static TwistStageRow from(YarnArticleTwistStage row) {
      return new TwistStageRow(
          row.getStageType(),
          row.getSequence(),
          row.getDirection(),
          row.getTurnsPerMeter(),
          row.getStrandComponentIndex(),
          row.getTestMethodId(),
          row.getTestMethod() == null ? null : row.getTestMethod().getCode());
    }
  }
}
