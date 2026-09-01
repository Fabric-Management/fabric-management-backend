package com.fabricmanagement.product.yarn.app;

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
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Application command. Derived fields and snapshot labels deliberately have no input slot. */
public record YarnArticleSpecCommand(
    CountSystem originalCountSystem,
    BigDecimal originalCountValue,
    CountBasis countBasis,
    YarnStructureType structureType,
    Integer foldCount,
    Integer filamentCount,
    BigDecimal twistContractionPercent,
    String sourceDesignation,
    YarnMaterialForm materialForm,
    SpinningTechnologyFamily spinningTechnologyFamily,
    UUID spinningSystemId,
    FilamentForm filamentForm,
    Set<YarnConstructionFeature> constructionFeatures,
    List<CompositionCommand> composition,
    List<ComponentCommand> structureComponents,
    List<TwistStageCommand> twistStages) {

  /** Empty, intentionally incomplete draft spec used when create omits its optional spec. */
  public static YarnArticleSpecCommand empty() {
    return draftCapture(null);
  }

  /** Empty canonical spec that captures supplier wording without parsing or normalization. */
  public static YarnArticleSpecCommand draftCapture(String sourceDesignation) {
    return new YarnArticleSpecCommand(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        sourceDesignation,
        null,
        null,
        null,
        null,
        Set.of(),
        List.of(),
        List.of(),
        List.of());
  }

  public YarnArticleSpecCommand {
    constructionFeatures =
        constructionFeatures == null ? Set.of() : Set.copyOf(constructionFeatures);
    composition = composition == null ? List.of() : List.copyOf(composition);
    structureComponents =
        structureComponents == null ? List.of() : List.copyOf(structureComponents);
    twistStages = twistStages == null ? List.of() : List.copyOf(twistStages);
  }

  public record CompositionCommand(UUID fiberId, BigDecimal percentage) {}

  public record ComponentCommand(
      ComponentKind kind,
      int componentIndex,
      LayerRole layerRole,
      CountSystem componentCountSystem,
      BigDecimal componentCountValue,
      UUID fiberId,
      String label) {}

  public record TwistStageCommand(
      TwistStageType stageType,
      int sequence,
      TwistDirection direction,
      BigDecimal turnsPerMeter,
      Integer strandComponentIndex,
      UUID testMethodId) {}
}
