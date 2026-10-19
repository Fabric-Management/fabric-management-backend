package com.fabricmanagement.product.fabric.domain.article;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.reference.FabricConstruction;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricArticleStatus;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricColourState;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
 * change to its spec raises its spec version.
 */
@Entity
@Table(name = "prod_fabric_article", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FabricArticle extends BaseEntity {

  public static final String CONSTRUCTION_MISSING = "CONSTRUCTION_MISSING";
  public static final String COLOUR_STATE_MISSING = "COLOUR_STATE_MISSING";
  public static final String GSM_FINISHED_MISSING = "GSM_FINISHED_MISSING";

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
    boolean changed = !currentSpec().sameAs(normalised(spec));

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
    if (colourState == null) problems.add(COLOUR_STATE_MISSING);
    if (gsmFinished == null) problems.add(GSM_FINISHED_MISSING);
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
        knitted == null ? null : knitted.spec());
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
            : spec.knitted() == null ? FabricArticleSpec.Knitted.EMPTY : spec.knitted());
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
