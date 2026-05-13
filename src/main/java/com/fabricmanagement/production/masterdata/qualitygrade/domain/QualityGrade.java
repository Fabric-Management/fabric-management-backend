package com.fabricmanagement.production.masterdata.qualitygrade.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tenant-specific quality grade definition for products.
 *
 * <p>Each tenant can define their own quality grading system per {@link ProductType}. Grades are
 * ordered by {@code rank} (1 = best) and carry a {@code priceFactor} that adjusts the base price
 * for the product.
 *
 * <h2>Grade Demotion Rule</h2>
 *
 * <pre>
 * 1A → 1B → 2 → OFF → WST  (downgrade = free)
 * WST → OFF → 2 → 1B → 1A  (upgrade = requires approval)
 * </pre>
 *
 * <p>A warehouse operator <b>can</b> downgrade (e.g. 1A → 2 due to damage) without approval. An
 * <b>upgrade</b> (e.g. 2 → 1A) requires the {@code requiresApproval} flag to be checked — the
 * service layer enforces this by comparing the old and new grades' {@code rank}.
 *
 * <h2>Seed Data</h2>
 *
 * <p>Default grades are seeded per tenant via {@code TenantCreatedEvent} listener:
 *
 * <pre>
 * FIBER: 1A(rank=1, pf=1.0), 1B(rank=2, pf=0.95), 2(rank=3, pf=0.80),
 *        OFF(rank=4, pf=0.40, saleable=true), WST(rank=5, pf=0.0, saleable=false)
 * YARN:  1A, 1B, 2, OFF, WST (similar)
 * FABRIC: 1A, 1B, 2, OFF, WST
 * </pre>
 *
 * @see ProductType
 */
@Entity
@Table(
    name = "quality_grade",
    schema = "production",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_quality_grade_tenant_product_code",
          columnNames = {"tenant_id", "product_type", "code"})
    },
    indexes = {
      @Index(
          name = "idx_quality_grade_tenant_product_active",
          columnList = "tenant_id, product_type, is_active")
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QualityGrade extends BaseEntity {

  /** The product type this grade applies to (FIBER, YARN, FABRIC, etc.). */
  @Enumerated(EnumType.STRING)
  @Column(name = "product_type", nullable = false, length = 20)
  private ProductType productType;

  /** Short code for this grade (e.g. "1A", "1B", "2", "OFF", "WST"). */
  @Column(name = "code", nullable = false, length = 10)
  private String code;

  /** Human-readable name (e.g. "Birinci Kalite A", "Fire", "Atık"). */
  @Column(name = "name", nullable = false, length = 100)
  private String name;

  /**
   * Ordering rank — lower is better. Used for demotion/promotion rule enforcement.
   *
   * <p>1 = best quality (e.g. 1A), 5 = worst (e.g. WST).
   */
  @Column(name = "rank", nullable = false)
  private int rank;

  /**
   * Price adjustment factor relative to base price.
   *
   * <p>1.000 = full price (grade 1A), 0.800 = 20% discount (grade 2), 0.000 = no value (waste).
   */
  @Column(name = "price_factor", nullable = false, precision = 5, scale = 3)
  @Builder.Default
  private BigDecimal priceFactor = BigDecimal.ONE;

  /** Whether this grade can be sold to customers. Waste (WST) is typically not saleable. */
  @Column(name = "saleable", nullable = false)
  @Builder.Default
  private boolean saleable = true;

  /**
   * Whether assigning this grade requires supervisor/QC manager approval.
   *
   * <p>Typically true for upgrades (moving to a better rank) and false for downgrades.
   */
  @Column(name = "requires_approval", nullable = false)
  @Builder.Default
  private boolean requiresApproval = false;

  /** UI color code for visual identification (e.g. "#00AA00" for green = good quality). */
  @Column(name = "color_hex", length = 7)
  private String colorHex;

  /** Whether this grade is automatically assigned to new stock units (one default per type). */
  @Column(name = "is_default", nullable = false)
  @Builder.Default
  private boolean isDefault = false;

  // ── Factory Methods ──────────────────────────────────────────────────────────

  /**
   * Creates a new QualityGrade with all required fields.
   *
   * @param tenantId tenant this grade belongs to
   * @param productType product type this grade applies to
   * @param code short unique code
   * @param name human-readable name
   * @param rank ordering rank (1 = best)
   * @param priceFactor price adjustment factor
   * @param saleable whether saleable
   * @param requiresApproval whether assignment requires approval
   * @param colorHex UI color code
   * @param isDefault whether this is the default for new stock units
   * @return new QualityGrade instance
   */
  public static QualityGrade create(
      UUID tenantId,
      ProductType productType,
      String code,
      String name,
      int rank,
      BigDecimal priceFactor,
      boolean saleable,
      boolean requiresApproval,
      String colorHex,
      boolean isDefault) {
    if (tenantId == null) {
      throw new IllegalArgumentException("QualityGrade tenantId must not be null");
    }
    if (productType == null) {
      throw new IllegalArgumentException("QualityGrade productType must not be null");
    }
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("QualityGrade code must not be blank");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("QualityGrade name must not be blank");
    }
    if (rank < 1) {
      throw new IllegalArgumentException("QualityGrade rank must be >= 1, got: " + rank);
    }
    if (priceFactor == null || priceFactor.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException(
          "QualityGrade priceFactor must be >= 0, got: " + priceFactor);
    }

    QualityGrade grade =
        QualityGrade.builder()
            .productType(productType)
            .code(code.toUpperCase().trim())
            .name(name.trim())
            .rank(rank)
            .priceFactor(priceFactor)
            .saleable(saleable)
            .requiresApproval(requiresApproval)
            .colorHex(colorHex)
            .isDefault(isDefault)
            .build();

    grade.setTenantId(tenantId);
    grade.onCreate();
    return grade;
  }

  // ── Domain Logic ─────────────────────────────────────────────────────────────

  /**
   * Determines whether transitioning from this grade to the target grade is a demotion (downgrade).
   *
   * <p>A demotion means moving to a worse quality (higher rank number). Demotions are allowed
   * without approval in most cases.
   *
   * @param target the target quality grade
   * @return true if target has a higher rank (worse quality) than this grade
   */
  public boolean isDemotionTo(QualityGrade target) {
    return target.rank > this.rank;
  }

  /**
   * Determines whether transitioning from this grade to the target grade is a promotion (upgrade).
   *
   * <p>A promotion means moving to a better quality (lower rank number). Promotions typically
   * require approval.
   *
   * @param target the target quality grade
   * @return true if target has a lower rank (better quality) than this grade
   */
  public boolean isPromotionTo(QualityGrade target) {
    return target.rank < this.rank;
  }

  /**
   * Checks if transitioning to the target grade requires approval.
   *
   * <p>Rules:
   *
   * <ul>
   *   <li>Promotion (upgrade) → always requires approval
   *   <li>Demotion with target's {@code requiresApproval} flag → requires approval
   *   <li>Demotion without flag → free
   * </ul>
   *
   * @param target the target quality grade
   * @return true if approval is needed for this transition
   */
  public boolean requiresApprovalForTransition(QualityGrade target) {
    if (isPromotionTo(target)) {
      return true;
    }
    return target.requiresApproval;
  }

  /**
   * Updates mutable fields. Code and productType are immutable after creation (part of unique
   * constraint).
   */
  public void update(
      String name,
      int rank,
      BigDecimal priceFactor,
      boolean saleable,
      boolean requiresApproval,
      String colorHex,
      boolean isDefault) {
    this.name = name;
    this.rank = rank;
    this.priceFactor = priceFactor;
    this.saleable = saleable;
    this.requiresApproval = requiresApproval;
    this.colorHex = colorHex;
    this.isDefault = isDefault;
    onUpdate();
  }

  @Override
  protected String getModuleCode() {
    return "QG";
  }
}
