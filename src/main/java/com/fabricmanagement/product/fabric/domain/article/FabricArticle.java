package com.fabricmanagement.product.fabric.domain.article;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.reference.FabricConstruction;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricArticleStatus;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricColourState;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricYarnRole;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Fabric master data (FABRIC-ARCHITECTURE F1): tenant-owned, one per FABRIC product. The product
 * carries the name and description (F0); the article carries what the fabric is. Formation is fixed
 * at creation, because the woven or knitted detail hangs on it.
 *
 * <p>Lifecycle: a DRAFT may be incomplete and may be deleted, since nothing has used it yet; only a
 * complete article becomes ACTIVE; an ACTIVE article is retired as OBSOLETE, never deleted, and a
 * change to its identity raises its spec version (recommended production settings never do).
 */
@Entity
@Table(name = "prod_fabric_article", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FabricArticle extends BaseEntity {

  public static final String CONSTRUCTION_MISSING = "CONSTRUCTION_MISSING";
  public static final String THREAD_DENSITY_MISSING = "THREAD_DENSITY_MISSING";
  public static final String MACHINE_GAUGE_MISSING = "MACHINE_GAUGE_MISSING";
  public static final String COMPOSITION_INCOMPLETE = "COMPOSITION_INCOMPLETE";
  public static final String YARN_SHARES_INCOMPLETE = "YARN_SHARES_INCOMPLETE";

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false, updatable = false)
  private Product product;

  @Column(name = "article_code", length = 60)
  private String articleCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private FabricArticleStatus status;

  @Column(name = "article_spec_version", nullable = false)
  private int articleSpecVersion = 1;

  @Enumerated(EnumType.STRING)
  @Column(name = "formation", nullable = false, length = 20, updatable = false)
  private FabricFormation formation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "construction_id")
  private FabricConstruction construction;

  @Enumerated(EnumType.STRING)
  @Column(name = "colour_state", length = 20)
  private FabricColourState colourState;

  @Column(name = "gsm_finished", precision = 7, scale = 1)
  private BigDecimal gsmFinished;

  @Column(name = "gsm_greige", precision = 7, scale = 1)
  private BigDecimal gsmGreige;

  @Column(name = "nominal_finished_width_cm", precision = 6, scale = 1)
  private BigDecimal nominalFinishedWidthCm;

  @Column(name = "usable_width_cm", precision = 6, scale = 1)
  private BigDecimal usableWidthCm;

  @Column(name = "nominal_greige_width_cm", precision = 6, scale = 1)
  private BigDecimal nominalGreigeWidthCm;

  @Column(name = "standard_roll_length_m", precision = 8, scale = 2)
  private BigDecimal standardRollLengthM;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  @OneToOne(
      mappedBy = "article",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private FabricWovenDetail woven;

  @OneToOne(
      mappedBy = "article",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private FabricKnittedDetail knitted;

  @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("position ASC")
  @Getter(AccessLevel.NONE)
  private List<FabricYarn> yarns = new ArrayList<>();

  @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("fiberIsoCode ASC")
  @Getter(AccessLevel.NONE)
  private List<FabricCompositionLine> composition = new ArrayList<>();

  /** A new, possibly incomplete article for a FABRIC product. */
  public static FabricArticle draft(Product product, FabricFormation formation) {
    if (product == null || product.getProductType() != ProductType.FABRIC) {
      throw new FabricDomainException("A fabric article belongs to a FABRIC product");
    }
    if (formation == null) {
      throw new FabricDomainException("A fabric article needs a formation");
    }
    FabricArticle article = new FabricArticle();
    article.product = product;
    article.formation = formation;
    article.status = FabricArticleStatus.DRAFT;
    if (formation == FabricFormation.WOVEN) {
      article.woven = new FabricWovenDetail(article);
    } else if (formation == FabricFormation.KNITTED) {
      article.knitted = new FabricKnittedDetail(article);
    }
    return article;
  }

  public UUID getProductId() {
    return product.getId();
  }

  /** Replaces the article's spec. An ACTIVE article must stay complete. */
  public void apply(FabricArticleSpec spec) {
    if (status == FabricArticleStatus.OBSOLETE) {
      throw new FabricDomainException("An obsolete fabric article cannot be changed");
    }
    if (spec.construction() != null) {
      if (spec.construction().getFormation() != formation) {
        throw new FabricDomainException(
            "A "
                + spec.construction().getFormation()
                + " construction does not fit a "
                + formation
                + " fabric");
      }
      boolean unchanged =
          construction != null && construction.getId().equals(spec.construction().getId());
      if (!unchanged && !spec.construction().isUsable()) {
        throw new FabricDomainException("The construction is no longer in use");
      }
    }
    if (formation != FabricFormation.WOVEN && spec.woven() != null) {
      throw new FabricDomainException("Only a woven fabric has woven detail");
    }
    if (formation != FabricFormation.KNITTED && spec.knitted() != null) {
      throw new FabricDomainException("Only a knitted fabric has knitted detail");
    }
    BigDecimal finished =
        FabricArticleSpec.positiveOrNull("nominalFinishedWidthCm", spec.nominalFinishedWidthCm());
    BigDecimal usable = FabricArticleSpec.positiveOrNull("usableWidthCm", spec.usableWidthCm());
    if (finished != null && usable != null && usable.compareTo(finished) > 0) {
      throw new FabricDomainException("The usable width cannot exceed the finished width");
    }
    List<FabricArticleSpec.YarnLine> yarnLines = checkedYarns(spec.yarns());
    List<FabricArticleSpec.CompositionLine> compositionLines =
        checkedComposition(spec.composition());
    boolean changed = normalised(spec).revisesIdentityOf(currentSpec());

    articleCode = FabricArticleSpec.trimToNull(spec.articleCode());
    construction = spec.construction();
    colourState = spec.colourState();
    gsmFinished = FabricArticleSpec.positiveOrNull("gsmFinished", spec.gsmFinished());
    gsmGreige = FabricArticleSpec.positiveOrNull("gsmGreige", spec.gsmGreige());
    nominalFinishedWidthCm = finished;
    usableWidthCm = usable;
    nominalGreigeWidthCm =
        FabricArticleSpec.positiveOrNull("nominalGreigeWidthCm", spec.nominalGreigeWidthCm());
    standardRollLengthM =
        FabricArticleSpec.positiveOrNull("standardRollLengthM", spec.standardRollLengthM());
    remarks = FabricArticleSpec.trimToNull(spec.remarks());
    if (woven != null) {
      woven.apply(spec.woven() == null ? FabricArticleSpec.Woven.EMPTY : spec.woven());
    }
    if (knitted != null) {
      knitted.apply(spec.knitted() == null ? FabricArticleSpec.Knitted.EMPTY : spec.knitted());
    }
    replaceYarns(yarnLines);
    replaceComposition(compositionLines);

    if (status == FabricArticleStatus.ACTIVE) {
      requireComplete("An active fabric article must stay complete");
      if (changed) {
        articleSpecVersion++;
      }
    }
  }

  /** The stable codes of what still stops this article from becoming ACTIVE. */
  public List<String> activationProblems() {
    List<String> problems = new ArrayList<>();
    if (construction == null) problems.add(CONSTRUCTION_MISSING);
    if (formation == FabricFormation.WOVEN
        && (woven == null || woven.getEndsPerCm() == null || woven.getPicksPerCm() == null)) {
      problems.add(THREAD_DENSITY_MISSING);
    }
    if (formation == FabricFormation.KNITTED
        && (knitted == null || knitted.getMachineGauge() == null)) {
      problems.add(MACHINE_GAUGE_MISSING);
    }
    if (composition.isEmpty()
        || FabricArticleSpec.total(
                    composition.stream().map(FabricCompositionLine::getPercentage).toList())
                .compareTo(HUNDRED)
            != 0) {
      problems.add(COMPOSITION_INCOMPLETE);
    }
    if (!yarns.isEmpty()
        && FabricArticleSpec.total(yarns.stream().map(FabricYarn::getSharePercent).toList())
                .compareTo(HUNDRED)
            != 0) {
      problems.add(YARN_SHARES_INCOMPLETE);
    }
    return problems;
  }

  public void activate() {
    if (status != FabricArticleStatus.DRAFT) {
      throw new FabricDomainException("Only a draft fabric article can be activated");
    }
    requireComplete("The fabric article is not complete yet");
    status = FabricArticleStatus.ACTIVE;
  }

  public void obsolete() {
    if (status != FabricArticleStatus.ACTIVE) {
      throw new FabricDomainException("Only an active fabric article can be made obsolete");
    }
    status = FabricArticleStatus.OBSOLETE;
  }

  /** Removes a never-used draft. An active article is retired as obsolete instead. */
  public void delete(Instant now) {
    if (status != FabricArticleStatus.DRAFT) {
      throw new FabricDomainException(
          "Only a draft fabric article can be deleted; make an active one obsolete instead");
    }
    setDeletedAt(now);
    setIsActive(false);
  }

  public FabricArticleSpec currentSpec() {
    return new FabricArticleSpec(
        articleCode,
        construction,
        colourState,
        gsmFinished,
        gsmGreige,
        nominalFinishedWidthCm,
        usableWidthCm,
        nominalGreigeWidthCm,
        standardRollLengthM,
        remarks,
        woven == null ? null : woven.spec(),
        knitted == null ? null : knitted.spec(),
        yarnLines(),
        compositionLines());
  }

  /** The yarns in role order, then position within the role. */
  public List<FabricArticleSpec.YarnLine> yarnLines() {
    return yarns.stream()
        .sorted(
            Comparator.comparing((FabricYarn yarn) -> yarn.getRole().ordinal())
                .thenComparing(FabricYarn::getPosition))
        .map(FabricYarn::line)
        .toList();
  }

  /** The declared composition, largest share first. */
  public List<FabricArticleSpec.CompositionLine> compositionLines() {
    return composition.stream()
        .sorted(
            Comparator.comparing(FabricCompositionLine::getPercentage)
                .reversed()
                .thenComparing(FabricCompositionLine::getFiberIsoCode))
        .map(FabricCompositionLine::line)
        .toList();
  }

  /** The detail an omitted section stands for, so "no woven values" compares as empty values. */
  private FabricArticleSpec normalised(FabricArticleSpec spec) {
    return new FabricArticleSpec(
        spec.articleCode(),
        spec.construction(),
        spec.colourState(),
        spec.gsmFinished(),
        spec.gsmGreige(),
        spec.nominalFinishedWidthCm(),
        spec.usableWidthCm(),
        spec.nominalGreigeWidthCm(),
        spec.standardRollLengthM(),
        spec.remarks(),
        woven == null ? null : spec.woven() == null ? FabricArticleSpec.Woven.EMPTY : spec.woven(),
        knitted == null
            ? null
            : spec.knitted() == null ? FabricArticleSpec.Knitted.EMPTY : spec.knitted(),
        spec.yarns(),
        spec.composition());
  }

  private List<FabricArticleSpec.YarnLine> checkedYarns(List<FabricArticleSpec.YarnLine> lines) {
    Set<String> seen = new HashSet<>();
    List<BigDecimal> shares = new ArrayList<>();
    for (FabricArticleSpec.YarnLine line : lines) {
      if (line == null || line.role() == null || line.yarnProductId() == null) {
        throw new FabricDomainException("Each fabric yarn needs a role and a yarn");
      }
      if (!line.role().fits(formation)) {
        throw new FabricDomainException(
            "A " + line.role() + " yarn does not fit a " + formation + " fabric");
      }
      if (!seen.add(line.role() + "/" + line.yarnProductId())) {
        throw new FabricDomainException(
            "The same yarn appears twice as " + line.role() + "; give it one combined share");
      }
      shares.add(FabricArticleSpec.percentage("sharePercent", line.sharePercent()));
    }
    if (FabricArticleSpec.total(shares).compareTo(HUNDRED) > 0) {
      throw new FabricDomainException("The yarn shares add up to more than 100%");
    }
    return lines;
  }

  private List<FabricArticleSpec.CompositionLine> checkedComposition(
      List<FabricArticleSpec.CompositionLine> lines) {
    Set<String> seen = new HashSet<>();
    List<BigDecimal> percentages = new ArrayList<>();
    for (FabricArticleSpec.CompositionLine line : lines) {
      if (line == null
          || line.fiberId() == null
          || FabricArticleSpec.trimToNull(line.isoCode()) == null
          || FabricArticleSpec.trimToNull(line.fiberName()) == null) {
        throw new FabricDomainException("Each composition line needs a fibre");
      }
      if (!seen.add(line.fiberId() + "/" + line.materialSource())) {
        throw new FabricDomainException(
            "Fibre " + line.isoCode() + " appears twice in the composition");
      }
      percentages.add(FabricArticleSpec.percentage("percentage", line.percentage()));
    }
    if (FabricArticleSpec.total(percentages).compareTo(HUNDRED) > 0) {
      throw new FabricDomainException("The composition adds up to more than 100%");
    }
    return lines;
  }

  /**
   * Keeps the rows whose role and position stay, so a re-save does not delete and re-insert them
   * (the database keeps one row per article, role and position).
   */
  private void replaceYarns(List<FabricArticleSpec.YarnLine> lines) {
    Map<String, FabricYarn> existing = new HashMap<>();
    yarns.forEach(yarn -> existing.put(yarn.getRole() + "/" + yarn.getPosition(), yarn));
    Map<FabricYarnRole, Short> nextPosition = new EnumMap<>(FabricYarnRole.class);
    List<FabricYarn> kept = new ArrayList<>();
    for (FabricArticleSpec.YarnLine line : lines) {
      short position = nextPosition.merge(line.role(), (short) 1, (a, b) -> (short) (a + b));
      FabricYarn yarn = existing.remove(line.role() + "/" + position);
      if (yarn == null) {
        yarn = new FabricYarn(this, line, position);
      } else {
        yarn.apply(line);
      }
      kept.add(yarn);
    }
    yarns.removeIf(current -> kept.stream().noneMatch(keep -> keep == current));
    kept.stream()
        .filter(keep -> yarns.stream().noneMatch(current -> current == keep))
        .forEach(yarns::add);
  }

  /** Keeps the rows whose fibre and source stay; the database keeps one row per pair. */
  private void replaceComposition(List<FabricArticleSpec.CompositionLine> lines) {
    Map<String, FabricCompositionLine> existing = new HashMap<>();
    composition.forEach(row -> existing.put(row.getFiberId() + "/" + row.getMaterialSource(), row));
    List<FabricCompositionLine> kept = new ArrayList<>();
    for (FabricArticleSpec.CompositionLine line : lines) {
      FabricCompositionLine row = existing.remove(line.fiberId() + "/" + line.materialSource());
      if (row == null) {
        row = new FabricCompositionLine(this, line);
      } else {
        row.apply(line);
      }
      kept.add(row);
    }
    composition.removeIf(current -> kept.stream().noneMatch(keep -> keep == current));
    kept.stream()
        .filter(keep -> composition.stream().noneMatch(current -> current == keep))
        .forEach(composition::add);
  }

  private void requireComplete(String message) {
    List<String> problems = activationProblems();
    if (!problems.isEmpty()) {
      throw new FabricDomainException(problems, message);
    }
  }

  @Override
  protected String getModuleCode() {
    return "FART";
  }
}
