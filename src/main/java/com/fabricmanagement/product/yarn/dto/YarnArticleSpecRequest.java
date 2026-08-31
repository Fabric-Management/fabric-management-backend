package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.app.YarnArticleSpecCommand;
import com.fabricmanagement.product.yarn.domain.article.ComponentKind;
import com.fabricmanagement.product.yarn.domain.article.LayerRole;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Editable yarn article specification; derived and snapshot fields are absent")
public record YarnArticleSpecRequest(
    CountSystem originalCountSystem,
    @DecimalMin(value = "0", inclusive = false) BigDecimal originalCountValue,
    CountBasis countBasis,
    YarnStructureType structureType,
    @Min(1) Integer foldCount,
    @Min(1) Integer filamentCount,
    @DecimalMin("0") @DecimalMax(value = "100", inclusive = false)
        BigDecimal twistContractionPercent,
    @Size(max = 255) String sourceDesignation,
    YarnMaterialForm materialForm,
    SpinningTechnologyFamily spinningTechnologyFamily,
    UUID spinningSystemId,
    FilamentForm filamentForm,
    Set<YarnConstructionFeature> constructionFeatures,
    List<@Valid CompositionRequest> composition,
    List<@Valid ComponentRequest> structureComponents,
    List<@Valid TwistStageRequest> twistStages) {

  public YarnArticleSpecCommand toCommand() {
    return new YarnArticleSpecCommand(
        originalCountSystem,
        originalCountValue,
        countBasis,
        structureType,
        foldCount,
        filamentCount,
        twistContractionPercent,
        sourceDesignation,
        materialForm,
        spinningTechnologyFamily,
        spinningSystemId,
        filamentForm,
        constructionFeatures,
        composition == null
            ? List.of()
            : composition.stream().map(CompositionRequest::toCommand).toList(),
        structureComponents == null
            ? List.of()
            : structureComponents.stream().map(ComponentRequest::toCommand).toList(),
        twistStages == null
            ? List.of()
            : twistStages.stream().map(TwistStageRequest::toCommand).toList());
  }

  @Schema(name = "YarnArticleCompositionRequest")
  public record CompositionRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID fiberId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull
          @DecimalMin(value = "0", inclusive = false)
          BigDecimal percentage) {

    YarnArticleSpecCommand.CompositionCommand toCommand() {
      return new YarnArticleSpecCommand.CompositionCommand(fiberId, percentage);
    }
  }

  @Schema(name = "YarnArticleComponentRequest")
  public record ComponentRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ComponentKind kind,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(1) int componentIndex,
      LayerRole layerRole,
      CountSystem componentCountSystem,
      @DecimalMin(value = "0", inclusive = false) BigDecimal componentCountValue,
      UUID fiberId,
      @Size(max = 100) String label) {

    YarnArticleSpecCommand.ComponentCommand toCommand() {
      return new YarnArticleSpecCommand.ComponentCommand(
          kind,
          componentIndex,
          layerRole,
          componentCountSystem,
          componentCountValue,
          fiberId,
          label);
    }
  }

  @Schema(name = "YarnArticleTwistStageRequest")
  public record TwistStageRequest(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull TwistStageType stageType,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @Min(1) int sequence,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull TwistDirection direction,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0")
          BigDecimal turnsPerMeter,
      @Min(1) Integer strandComponentIndex,
      UUID testMethodId) {

    YarnArticleSpecCommand.TwistStageCommand toCommand() {
      return new YarnArticleSpecCommand.TwistStageCommand(
          stageType, sequence, direction, turnsPerMeter, strandComponentIndex, testMethodId);
    }
  }
}
