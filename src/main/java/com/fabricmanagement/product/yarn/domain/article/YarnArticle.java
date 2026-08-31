package com.fabricmanagement.product.yarn.domain.article;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.reference.YarnSpinningSystem;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountBasis;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountSystem;
import com.fabricmanagement.product.yarn.domain.vocabulary.FilamentForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnConstructionFeature;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnMaterialForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnStructureType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "prod_yarn_article", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnArticle extends BaseEntity {

  public static final short CANONICAL_KEY_ALGORITHM_VERSION = 1;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false, unique = true, updatable = false)
  private Product product;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private YarnArticleStatus status;

  @Column(name = "article_spec_version", nullable = false)
  private int articleSpecVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "original_count_system", length = 20)
  private CountSystem originalCountSystem;

  @Column(name = "original_count_value", precision = 18, scale = 6)
  private BigDecimal originalCountValue;

  @Enumerated(EnumType.STRING)
  @Column(name = "count_basis", length = 20)
  private CountBasis countBasis;

  @Enumerated(EnumType.STRING)
  @Column(name = "structure_type", length = 30)
  private YarnStructureType structureType;

  @Column(name = "fold_count")
  private Integer foldCount;

  @Column(name = "filament_count")
  private Integer filamentCount;

  @Column(name = "twist_contraction_percent", precision = 5, scale = 2)
  private BigDecimal twistContractionPercent;

  @Column(name = "resultant_linear_density_tex", precision = 18, scale = 2)
  private BigDecimal resultantLinearDensityTex;

  @Column(name = "canonical_designation", length = 160)
  private String canonicalDesignation;

  /** Supplier wording is stored verbatim and is never parsed. */
  @Column(name = "source_designation", length = 255)
  private String sourceDesignation;

  @Enumerated(EnumType.STRING)
  @Column(name = "material_form", length = 30)
  private YarnMaterialForm materialForm;

  @Enumerated(EnumType.STRING)
  @Column(name = "spinning_technology_family", length = 30)
  private SpinningTechnologyFamily spinningTechnologyFamily;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "spinning_system_id")
  private YarnSpinningSystem spinningSystemRef;

  @Enumerated(EnumType.STRING)
  @Column(name = "filament_form", length = 30)
  private FilamentForm filamentForm;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "canonical_key", length = 64, columnDefinition = "char(64)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String canonicalKey;

  @Column(name = "canonical_key_algorithm_version", nullable = false)
  private short canonicalKeyAlgorithmVersion;

  @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
  private final List<YarnArticleComposition> composition = new ArrayList<>();

  @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("kind ASC, componentIndex ASC")
  private final List<YarnArticleStructureComponent> structureComponents = new ArrayList<>();

  @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sequence ASC")
  private final List<YarnArticleTwistStage> twistStages = new ArrayList<>();

  @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
  private final List<YarnArticleConstructionFeature> constructionFeatures = new ArrayList<>();

  public static YarnArticle createDraft(
      Product product,
      String name,
      String description,
      YarnArticleSpec spec,
      YarnArticleSpecSerializer serializer) {
    UUID tenantId = TenantContext.requireTenantId();
    if (product == null
        || product.getProductType() != ProductType.YARN
        || !Boolean.TRUE.equals(product.getIsActive())
        || !tenantId.equals(product.getTenantId())) {
      throw new YarnDomainException("I18", "I18: Product must be an active current-tenant YARN");
    }
    YarnArticle article = new YarnArticle();
    article.product = product;
    article.status = YarnArticleStatus.DRAFT;
    article.articleSpecVersion = 1;
    article.canonicalKeyAlgorithmVersion = CANONICAL_KEY_ALGORITHM_VERSION;
    article.name = requireName(name);
    article.description = trimToNull(description);
    article.replaceSpecState(spec, serializer);
    return article;
  }

  public void updateSpec(YarnArticleSpec spec, YarnArticleSpecSerializer serializer) {
    if (status == YarnArticleStatus.OBSOLETE) {
      throw new YarnDomainException("I17", "I17: OBSOLETE articles reject spec mutation");
    }
    replaceSpecState(spec, serializer);
    if (status == YarnArticleStatus.ACTIVE) {
      YarnArticleInvariantCatalog.requireFullValid(this);
    }
    articleSpecVersion++;
  }

  public void updateMetadata(String name, String description) {
    this.name = requireName(name);
    this.description = trimToNull(description);
  }

  public List<YarnArticleInvariantCatalog.Violation> activationViolations() {
    return YarnArticleInvariantCatalog.validateFull(this);
  }

  @Override
  public void activate() {
    if (status != YarnArticleStatus.DRAFT) {
      throw new YarnDomainException("I17", "I17: only DRAFT can transition to ACTIVE");
    }
    YarnArticleInvariantCatalog.requireFullValid(this);
    status = YarnArticleStatus.ACTIVE;
  }

  public void markObsolete() {
    if (status != YarnArticleStatus.ACTIVE) {
      throw new YarnDomainException("I17", "I17: only ACTIVE can transition to OBSOLETE");
    }
    status = YarnArticleStatus.OBSOLETE;
  }

  List<YarnArticleStructureComponent> strands() {
    return structureComponents.stream()
        .filter(component -> component.getKind() == ComponentKind.STRAND)
        .toList();
  }

  List<YarnArticleStructureComponent> layers() {
    return structureComponents.stream()
        .filter(component -> component.getKind() == ComponentKind.LAYER)
        .toList();
  }

  public boolean hasFeature(YarnConstructionFeature feature) {
    return constructionFeatures.stream().anyMatch(row -> row.getFeature() == feature);
  }

  public UUID getProductId() {
    return product == null ? null : product.getId();
  }

  private void replaceSpecState(YarnArticleSpec spec, YarnArticleSpecSerializer serializer) {
    Objects.requireNonNull(spec, "spec");
    validatePreConversionBounds(spec);
    originalCountSystem = spec.originalCountSystem();
    originalCountValue = spec.originalCountValue();
    countBasis = spec.countBasis();
    structureType = spec.structureType();
    foldCount = spec.foldCount();
    filamentCount = spec.filamentCount();
    twistContractionPercent =
        spec.twistContractionPercent() == null
            ? null
            : spec.twistContractionPercent().setScale(2, RoundingMode.HALF_UP);
    sourceDesignation = spec.sourceDesignation();
    materialForm = spec.materialForm();
    spinningTechnologyFamily = spec.spinningTechnologyFamily();
    spinningSystemRef = spec.spinningSystemRef();
    filamentForm = spec.filamentForm();

    twistStages.clear();
    structureComponents.clear();
    composition.clear();
    constructionFeatures.clear();

    spec.constructionFeatures().stream()
        .sorted()
        .forEach(
            feature ->
                constructionFeatures.add(YarnArticleConstructionFeature.create(this, feature)));
    spec.composition().forEach(input -> appendComposition(input.fiber(), input.percentage()));
    spec.structureComponents().forEach(this::appendStructureComponent);
    spec.twistStages().forEach(this::appendTwistStage);

    YarnArticleInvariantCatalog.requireWriteValid(this);
    resultantLinearDensityTex =
        YarnArticleDerivation.resultantTex(
            originalCountSystem,
            originalCountValue,
            countBasis,
            foldCount,
            structureComponents,
            twistContractionPercent);
    canonicalDesignation =
        YarnArticleDerivation.designation(
            originalCountSystem,
            originalCountValue,
            foldCount,
            countBasis,
            filamentCount,
            resultantLinearDensityTex);
    canonicalKey = serializer.canonicalKeyIfComplete(this);
  }

  void appendComposition(
      com.fabricmanagement.product.fiber.domain.Fiber fiber, BigDecimal percentage) {
    YarnArticleComposition row = YarnArticleComposition.create(this, fiber, percentage);
    assertSourceConsistent(row.getFiberId(), row.getMaterialSource());
    composition.add(row);
  }

  void appendStructureComponent(YarnArticleSpec.ComponentInput input) {
    YarnArticleStructureComponent row = YarnArticleStructureComponent.create(this, input);
    if (row.getFiberId() != null) {
      assertSourceConsistent(row.getFiberId(), row.getMaterialSource());
    }
    structureComponents.add(row);
  }

  private void appendTwistStage(YarnArticleSpec.TwistStageInput input) {
    YarnArticleStructureComponent component = null;
    if (input.strandComponentIndex() != null) {
      component =
          structureComponents.stream()
              .filter(
                  row ->
                      row.getKind() == ComponentKind.STRAND
                          && row.getComponentIndex() == input.strandComponentIndex())
              .findFirst()
              .orElseThrow(
                  () -> new YarnDomainException("I6", "I6: stage references a missing STRAND"));
    }
    twistStages.add(YarnArticleTwistStage.create(this, input, component));
  }

  private void assertSourceConsistent(
      UUID fiberId, com.fabricmanagement.product.fiber.domain.MaterialSource source) {
    boolean mismatch =
        composition.stream()
                .anyMatch(
                    row -> row.getFiberId().equals(fiberId) && row.getMaterialSource() != source)
            || structureComponents.stream()
                .anyMatch(
                    row -> fiberId.equals(row.getFiberId()) && row.getMaterialSource() != source);
    if (mismatch) {
      throw new YarnDomainException(
          "I31", "I31: material-source snapshots disagree for Fiber " + fiberId);
    }
  }

  private static void validatePreConversionBounds(YarnArticleSpec spec) {
    boolean parentPair =
        (spec.originalCountSystem() == null) == (spec.originalCountValue() == null);
    boolean componentPairs =
        spec.structureComponents().stream()
            .allMatch(
                component ->
                    (component.componentCountSystem() == null)
                        == (component.componentCountValue() == null));
    if (!parentPair || !componentPairs) {
      throw new YarnDomainException("I30", "I30: count system/value pairs must be complete");
    }
    if ((spec.originalCountValue() != null && spec.originalCountValue().signum() <= 0)
        || (spec.foldCount() != null && spec.foldCount() < 1)
        || (spec.filamentCount() != null && spec.filamentCount() < 1)
        || spec.structureComponents().stream()
            .anyMatch(
                component ->
                    component.componentCountValue() != null
                        && component.componentCountValue().signum() <= 0)
        || spec.twistStages().stream()
            .anyMatch(
                stage -> stage.turnsPerMeter() != null && stage.turnsPerMeter().signum() < 0)) {
      throw new YarnDomainException("I29", "I29: physical values are outside their bounds");
    }
    if (spec.twistContractionPercent() != null
        && (spec.twistContractionPercent().signum() < 0
            || spec.twistContractionPercent().compareTo(new BigDecimal("100")) >= 0)) {
      throw new YarnDomainException("I28", "I28: twistContractionPercent must be in [0,100)");
    }
  }

  private static String requireName(String value) {
    if (value == null || value.isBlank()) {
      throw new YarnDomainException("Yarn article name is required");
    }
    return value.trim();
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  @Override
  protected String getModuleCode() {
    return "YART";
  }
}
