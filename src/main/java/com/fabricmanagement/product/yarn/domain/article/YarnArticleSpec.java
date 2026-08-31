package com.fabricmanagement.product.yarn.domain.article;

import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.yarn.domain.reference.YarnSpinningSystem;
import com.fabricmanagement.product.yarn.domain.reference.YarnTestMethod;
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

/** Resolved aggregate input. Snapshot and derived fields are deliberately absent. */
public record YarnArticleSpec(
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
    YarnSpinningSystem spinningSystemRef,
    FilamentForm filamentForm,
    Set<YarnConstructionFeature> constructionFeatures,
    List<CompositionInput> composition,
    List<ComponentInput> structureComponents,
    List<TwistStageInput> twistStages) {

  public YarnArticleSpec {
    constructionFeatures =
        constructionFeatures == null ? Set.of() : Set.copyOf(constructionFeatures);
    composition = composition == null ? List.of() : List.copyOf(composition);
    structureComponents =
        structureComponents == null ? List.of() : List.copyOf(structureComponents);
    twistStages = twistStages == null ? List.of() : List.copyOf(twistStages);
  }

  public record CompositionInput(Fiber fiber, BigDecimal percentage) {}

  public record ComponentInput(
      ComponentKind kind,
      int componentIndex,
      LayerRole layerRole,
      CountSystem componentCountSystem,
      BigDecimal componentCountValue,
      Fiber fiber,
      String label) {}

  public record TwistStageInput(
      TwistStageType stageType,
      int sequence,
      TwistDirection direction,
      BigDecimal turnsPerMeter,
      Integer strandComponentIndex,
      YarnTestMethod testMethod) {}
}
