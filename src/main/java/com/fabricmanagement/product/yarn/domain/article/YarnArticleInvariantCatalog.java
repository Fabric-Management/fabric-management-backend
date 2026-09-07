package com.fabricmanagement.product.yarn.domain.article;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.yarn.domain.SourceDesignationPolicy;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountBasis;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistDirection;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistStageType;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnConstructionFeature;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnMaterialForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnStructureType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/** The executable I1-I32 catalogue. Tests enumerate this class as their single rule source. */
public final class YarnArticleInvariantCatalog {

  private static final String TWIST_PROPERTY_KEY = "YARN_TWIST_TPM";

  public record Violation(String id, String message) {}

  public static final List<String> ALL_IDS =
      IntStream.rangeClosed(1, 32).mapToObj(number -> "I" + number).toList();

  public static final Set<String> WRITE_TIME_IDS =
      Set.of(
          "I1", "I2", "I3", "I4", "I5", "I6", "I7", "I8", "I9", "I10", "I11", "I12", "I13", "I14",
          "I23", "I24", "I25", "I26", "I27", "I28", "I29", "I30", "I31", "I32");

  private YarnArticleInvariantCatalog() {}

  public static List<Violation> validateWrite(YarnArticle article) {
    return validate(article, false).stream()
        .filter(violation -> WRITE_TIME_IDS.contains(violation.id()))
        .toList();
  }

  public static List<Violation> validateFull(YarnArticle article) {
    return validate(article, true);
  }

  public static void requireWriteValid(YarnArticle article) {
    throwIfAny(validateWrite(article));
  }

  public static void requireFullValid(YarnArticle article) {
    throwIfAny(validateFull(article));
  }

  private static void throwIfAny(List<Violation> violations) {
    if (!violations.isEmpty()) {
      List<String> ids = violations.stream().map(Violation::id).distinct().toList();
      throw new YarnDomainException(
          ids,
          String.join("; ", violations.stream().map(v -> v.id() + ": " + v.message()).toList()));
    }
  }

  private static List<Violation> validate(YarnArticle article, boolean activation) {
    Map<String, String> failures = new LinkedHashMap<>();
    var structure = article.getStructureType();
    Integer fold = article.getFoldCount();
    List<YarnArticleStructureComponent> strands = article.strands();
    List<YarnArticleStructureComponent> layers = article.layers();
    List<YarnArticleTwistStage> stages = article.getTwistStages();

    if (structure == YarnStructureType.SINGLE && !Integer.valueOf(1).equals(fold)) {
      fail(failures, "I1", "SINGLE requires foldCount = 1");
    }
    if ((structure == YarnStructureType.PLIED || structure == YarnStructureType.CABLED)
        && (fold == null || fold < 2)) {
      fail(failures, "I2", "PLIED/CABLED requires foldCount >= 2");
    }
    if (structure == YarnStructureType.MULTIPLE_WOUND) {
      if (fold == null
          || fold < 2
          || stages.stream()
              .anyMatch(
                  s ->
                      s.getStageType() == TwistStageType.PLY
                          || s.getStageType() == TwistStageType.CABLE)) {
        fail(failures, "I3", "MULTIPLE_WOUND requires foldCount >= 2 and no PLY/CABLE stage");
      }
    }
    if (!strands.isEmpty()) {
      if (!Set.of(
                  YarnStructureType.PLIED,
                  YarnStructureType.CABLED,
                  YarnStructureType.MULTIPLE_WOUND)
              .contains(structure)
          || fold == null
          || strands.size() != fold) {
        fail(
            failures,
            "I4",
            "STRAND rows require a multi-strand structure and must equal foldCount");
      }
    }

    boolean compound =
        article.hasFeature(YarnConstructionFeature.CORE_SPUN)
            || article.hasFeature(YarnConstructionFeature.COVERED);
    if (article.hasFeature(YarnConstructionFeature.CORE_SPUN)
        && !validLayerShape(layers, activation)) {
      fail(
          failures,
          "I5",
          "CORE_SPUN requires one CORE and at least one SHEATH; ACTIVE layers require Fiber");
    }
    if (stages.stream()
        .anyMatch(
            stage ->
                stage.getComponent() != null
                    && (!strands.contains(stage.getComponent())
                        || stage.getComponent().getKind() != ComponentKind.STRAND))) {
      fail(failures, "I6", "a twist-stage component must be a STRAND of this article");
    }
    if (activation
        && article.getMaterialForm() == YarnMaterialForm.STAPLE_SPUN
        && stages.stream().noneMatch(stage -> stage.getStageType() == TwistStageType.SINGLE)) {
      fail(failures, "I7", "ACTIVE staple-spun yarn requires a SINGLE stage");
    }
    long plyCount =
        stages.stream().filter(stage -> stage.getStageType() == TwistStageType.PLY).count();
    long cableCount =
        stages.stream().filter(stage -> stage.getStageType() == TwistStageType.CABLE).count();
    if ((structure == YarnStructureType.PLIED && (plyCount > 1 || (activation && plyCount != 1)))
        || (structure == YarnStructureType.CABLED
            && (cableCount > 1 || (activation && (cableCount != 1 || plyCount < 1))))) {
      fail(failures, "I8", "PLIED/CABLED twist-stage shape is invalid");
    }
    for (YarnArticleTwistStage stage : stages) {
      BigDecimal tpm = stage.getTurnsPerMeter();
      boolean none = stage.getDirection() == TwistDirection.NONE;
      if (stage.getDirection() == null
          || tpm == null
          || (none && tpm.signum() != 0)
          || (!none && tpm.signum() <= 0)) {
        fail(failures, "I9", "twist direction and turnsPerMeter disagree");
      }
    }
    if (!contiguous(stages.stream().map(YarnArticleTwistStage::getSequence).toList())) {
      fail(failures, "I10", "twist-stage sequences must be unique and contiguous");
    }
    if (stages.stream()
        .anyMatch(
            stage ->
                stage.getTestMethod() != null
                    && stage.getTestMethod().getApplicablePropertyKey() != null
                    && !TWIST_PROPERTY_KEY.equals(
                        stage.getTestMethod().getApplicablePropertyKey()))) {
      fail(failures, "I11", "twist test method is not applicable to YARN_TWIST_TPM");
    }
    if (article.getMaterialForm() == YarnMaterialForm.STAPLE_SPUN
        && (article.getSpinningTechnologyFamily() == null
            || article.getFilamentCount() != null
            || article.getFilamentForm() != null)) {
      fail(failures, "I12", "STAPLE_SPUN requires spinning family and forbids filament fields");
    }
    if (article.getMaterialForm() == YarnMaterialForm.CONTINUOUS_FILAMENT
        && (article.getFilamentForm() == null
            || article.getFilamentCount() == null
            || article.getSpinningTechnologyFamily() != null)) {
      fail(
          failures,
          "I12",
          "CONTINUOUS_FILAMENT requires filament fields and forbids spinning family");
    }
    if (article.getSpinningSystemRef() != null
        && article.getSpinningSystemRef().getTechnologyFamily()
            != article.getSpinningTechnologyFamily()) {
      fail(failures, "I13", "spinning-system family must match the article family");
    }
    if (!compositionValid(article.getComposition())) {
      fail(failures, "I14", "composition rows require unique pure Fibers and positive percentages");
    }

    if (activation
        && (!activationInputsComplete(article)
            || article.getComposition().isEmpty()
            || article.getComposition().stream()
                    .map(YarnArticleComposition::getPercentage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP)
                    .compareTo(new BigDecimal("100.00"))
                != 0)) {
      fail(failures, "I15", "ACTIVE article is incomplete or composition does not total 100.00");
    }
    if (article.getStatus() == YarnArticleStatus.OBSOLETE && activation) {
      fail(failures, "I17", "OBSOLETE articles cannot be activated or edited");
    }
    if (article.getProduct() == null
        || article.getProduct().getProductType() != ProductType.YARN
        || !Boolean.TRUE.equals(article.getProduct().getIsActive())) {
      fail(failures, "I18", "article requires an active YARN Product");
    }
    if (activation && !singleStageBindingsValid(strands, stages)) {
      fail(
          failures,
          "I21",
          "each STRAND requires exactly one bound SINGLE stage and no unbound SINGLE stage");
    }
    if (activation && !resultantReconciles(article, strands)) {
      fail(failures, "I22", "stored resultant does not match the current derivation/count");
    }
    if ((!layers.isEmpty() && !compound)
        || (article.hasFeature(YarnConstructionFeature.COVERED)
            && !validLayerShape(layers, activation))) {
      fail(
          failures,
          "I23",
          "LAYER rows require CORE_SPUN/COVERED and both require core/sheath shape");
    }
    if ((!article.getComposition().isEmpty() || activation)
        && article.getStructureComponents().stream()
            .anyMatch(
                component ->
                    component.getFiberId() != null
                        && article.getComposition().stream()
                            .noneMatch(row -> row.getFiberId().equals(component.getFiberId())))) {
      fail(failures, "I24", "every component Fiber must appear in declared composition");
    }
    if (layers.stream()
        .anyMatch(
            layer ->
                layer.getComponentCountSystem() != null
                    || layer.getComponentCountValue() != null
                    || layer.getComponentLinearDensityTex() != null)) {
      fail(failures, "I27", "LAYER rows cannot carry count values");
    }
    BigDecimal contraction = article.getTwistContractionPercent();
    if (contraction != null
        && (contraction.signum() < 0 || contraction.compareTo(new BigDecimal("100")) >= 0)) {
      fail(failures, "I28", "twistContractionPercent must be in [0,100)");
    }
    if ((article.getFilamentCount() != null && article.getFilamentCount() < 1)
        || (article.getOriginalCountValue() != null
            && article.getOriginalCountValue().signum() <= 0)
        || (article.getFoldCount() != null && article.getFoldCount() < 1)
        || strands.stream()
            .anyMatch(
                row ->
                    row.getComponentCountValue() != null
                        && row.getComponentCountValue().signum() <= 0)
        || stages.stream()
            .anyMatch(
                row -> row.getTurnsPerMeter() != null && row.getTurnsPerMeter().signum() < 0)) {
      fail(failures, "I29", "one or more physical values are outside their bounds");
    }
    boolean parentPair =
        (article.getOriginalCountSystem() == null) == (article.getOriginalCountValue() == null);
    boolean componentPairs =
        strands.stream()
            .allMatch(
                row ->
                    (row.getComponentCountSystem() == null)
                        == (row.getComponentCountValue() == null));
    boolean derivationComplete =
        !activation
            || (strands.isEmpty()
                ? article.getOriginalCountSystem() != null
                : strands.stream().allMatch(row -> row.getComponentCountSystem() != null));
    if (!parentPair || !componentPairs || !derivationComplete) {
      fail(
          failures,
          "I30",
          "count systems and values must be paired and the active derivation branch complete");
    }
    if (!sourceSnapshotsConsistent(article)) {
      fail(failures, "I31", "one Fiber has conflicting material-source snapshots");
    }
    if (article.getStatus() != YarnArticleStatus.OBSOLETE
        && (SourceDesignationPolicy.isBlank(article.getSourceDesignation())
            || SourceDesignationPolicy.isOverlength(article.getSourceDesignation()))) {
      fail(
          failures,
          "I32",
          "sourceDesignation must be null or non-blank and at most 255 code points");
    }

    return failures.entrySet().stream()
        .map(entry -> new Violation(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static boolean validLayerShape(
      List<YarnArticleStructureComponent> layers, boolean activation) {
    long cores = layers.stream().filter(layer -> layer.getLayerRole() == LayerRole.CORE).count();
    long sheaths =
        layers.stream().filter(layer -> layer.getLayerRole() == LayerRole.SHEATH).count();
    return cores == 1
        && sheaths >= 1
        && (!activation || layers.stream().allMatch(layer -> layer.getFiber() != null));
  }

  private static boolean contiguous(List<Integer> values) {
    if (values.isEmpty()) {
      return true;
    }
    Set<Integer> unique = new HashSet<>(values);
    if (unique.size() != values.size()) {
      return false;
    }
    for (int index = 1; index <= values.size(); index++) {
      if (!unique.contains(index)) {
        return false;
      }
    }
    return true;
  }

  private static boolean compositionValid(List<YarnArticleComposition> rows) {
    Set<UUID> ids = new HashSet<>();
    return rows.stream()
        .allMatch(
            row ->
                row.getFiber() != null
                    && row.getFiber().isPure()
                    && row.getPercentage() != null
                    && row.getPercentage().signum() > 0
                    && ids.add(row.getFiberId()));
  }

  private static boolean activationInputsComplete(YarnArticle article) {
    return article.getCountBasis() != null
        && article.getStructureType() != null
        && article.getFoldCount() != null
        && article.getMaterialForm() != null
        && article.getResultantLinearDensityTex() != null
        && article.getCanonicalDesignation() != null;
  }

  private static boolean singleStageBindingsValid(
      List<YarnArticleStructureComponent> strands, List<YarnArticleTwistStage> stages) {
    if (strands.isEmpty()) {
      return true;
    }
    List<YarnArticleTwistStage> single =
        stages.stream().filter(stage -> stage.getStageType() == TwistStageType.SINGLE).toList();
    return single.stream().noneMatch(stage -> stage.getComponent() == null)
        && strands.stream()
            .allMatch(
                strand ->
                    single.stream().filter(stage -> stage.getComponent() == strand).count() == 1);
  }

  private static boolean resultantReconciles(
      YarnArticle article, List<YarnArticleStructureComponent> strands) {
    BigDecimal expected =
        YarnArticleDerivation.resultantTex(
            article.getOriginalCountSystem(),
            article.getOriginalCountValue(),
            article.getCountBasis(),
            article.getFoldCount(),
            article.getStructureComponents(),
            article.getTwistContractionPercent());
    if (expected == null
        || article.getResultantLinearDensityTex() == null
        || expected.compareTo(article.getResultantLinearDensityTex()) != 0) {
      return false;
    }
    if (!strands.isEmpty() && article.getOriginalCountSystem() != null) {
      if (article.getCountBasis() != CountBasis.RESULTANT) {
        return false;
      }
      BigDecimal converted =
          YarnArticleDerivation.componentTex(
              article.getOriginalCountSystem(), article.getOriginalCountValue());
      return converted.compareTo(expected) == 0;
    }
    return true;
  }

  private static boolean sourceSnapshotsConsistent(YarnArticle article) {
    Map<UUID, Object> sourceByFiber = new HashMap<>();
    Object nullMarker = new Object();
    List<Object[]> rows = new ArrayList<>();
    article
        .getComposition()
        .forEach(row -> rows.add(new Object[] {row.getFiberId(), row.getMaterialSource()}));
    article.getStructureComponents().stream()
        .filter(row -> row.getFiberId() != null)
        .forEach(row -> rows.add(new Object[] {row.getFiberId(), row.getMaterialSource()}));
    for (Object[] row : rows) {
      UUID fiberId = (UUID) row[0];
      Object source = row[1] == null ? nullMarker : row[1];
      Object previous = sourceByFiber.putIfAbsent(fiberId, source);
      if (previous != null && previous != source && !previous.equals(source)) {
        return false;
      }
    }
    return true;
  }

  private static void fail(Map<String, String> failures, String id, String message) {
    failures.putIfAbsent(id, message);
  }
}
