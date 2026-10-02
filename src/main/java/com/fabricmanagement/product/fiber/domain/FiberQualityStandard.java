package com.fabricmanagement.product.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Type;

/**
 * Tenant quality profile (LSL / Target / USL) for a shared ISO code or an exact fibre/mixture
 * (FIBER-CATALOG-1).
 *
 * <p>The tenant always owns the profile even when its target is a shared record. An ISO_CODE
 * profile applies to pure fibres of that ISO only; a FIBER profile applies to that exact fibre with
 * the composition captured at creation ({@code {fiberId: 100}} for a pure fibre). The target never
 * changes after creation. {@code isDefault} marks the one active default per tenant and target used
 * by the shared resolver; a blend never borrows its dominant component's profile.
 */
@Entity
@Table(
    name = "prod_fiber_quality_standard",
    schema = "production",
    indexes = {
      @Index(name = "idx_fiber_quality_standard_tenant", columnList = "tenant_id"),
      @Index(name = "idx_fiber_quality_standard_iso_code", columnList = "iso_code_id"),
      @Index(
          name = "idx_fiber_quality_standard_fiber",
          columnList = "tenant_id,fiber_id,is_default")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberQualityStandard extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 20, updatable = false)
  private FiberQualityTargetType targetType;

  /** Shared ISO code; set only for an ISO_CODE target. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "iso_code_id", updatable = false)
  private FiberIsoCode isoCode;

  /** Exact target fibre id; set only for a FIBER target. */
  @Column(name = "fiber_id", updatable = false)
  private UUID fiberId;

  /** FIBER target only: normalised composition captured at creation; immutable. */
  @Type(JsonType.class)
  @Column(name = "target_composition", columnDefinition = "jsonb", updatable = false)
  private Map<UUID, BigDecimal> targetComposition;

  @Column(name = "standard_name", nullable = false, length = 100)
  private String standardName;

  @Column(name = "is_default", nullable = false)
  @Builder.Default
  private Boolean isDefault = false;

  // ── Fineness (micronaire / dtex) ──────────────────────────────────────────

  @Column(name = "fineness_min")
  private Double finenessMin;

  @Column(name = "fineness_target")
  private Double finenessTarget;

  @Column(name = "fineness_max")
  private Double finenessMax;

  // ── Staple length (mm) ─────────────────────────────────────────────────────

  @Column(name = "length_mm_min")
  private Double lengthMmMin;

  @Column(name = "length_mm_target")
  private Double lengthMmTarget;

  @Column(name = "length_mm_max")
  private Double lengthMmMax;

  // ── Tenacity / Strength (cN/dtex) ───────────────────────────────────────────

  @Column(name = "strength_cnd_tex_min")
  private Double strengthCndTexMin;

  @Column(name = "strength_cnd_tex_target")
  private Double strengthCndTexTarget;

  @Column(name = "strength_cnd_tex_max")
  private Double strengthCndTexMax;

  // ── Elongation at break (%) ────────────────────────────────────────────────

  @Column(name = "elongation_pct_min")
  private Double elongationPctMin;

  @Column(name = "elongation_pct_target")
  private Double elongationPctTarget;

  @Column(name = "elongation_pct_max")
  private Double elongationPctMax;

  // ── Moisture / humidity (%) ─────────────────────────────────────────────────

  @Column(name = "moisture_pct_min")
  private Double moisturePctMin;

  @Column(name = "moisture_pct_target")
  private Double moisturePctTarget;

  @Column(name = "moisture_pct_max")
  private Double moisturePctMax;

  // ── Trash & neps content (%) ──────────────────────────────────────────────

  @Column(name = "trash_content_pct_min")
  private Double trashContentPctMin;

  @Column(name = "trash_content_pct_target")
  private Double trashContentPctTarget;

  @Column(name = "trash_content_pct_max")
  private Double trashContentPctMax;

  // ── Uniformity index (%) ──────────────────────────────────────────────────

  @Column(name = "uniformity_index_min")
  private Double uniformityIndexMin;

  @Column(name = "uniformity_index_target")
  private Double uniformityIndexTarget;

  @Column(name = "uniformity_index_max")
  private Double uniformityIndexMax;

  public UUID getIsoCodeId() {
    return isoCode != null ? isoCode.getId() : null;
  }

  /** ISO_CODE profile for a shared ISO code. */
  public static FiberQualityStandard forIsoCode(FiberIsoCode isoCode, String standardName) {
    FiberQualityStandard standard = new FiberQualityStandard();
    standard.targetType = FiberQualityTargetType.ISO_CODE;
    standard.isoCode = isoCode;
    standard.standardName = standardName;
    standard.isDefault = false;
    return standard;
  }

  /** FIBER profile for one exact fibre and its captured, normalised composition. */
  public static FiberQualityStandard forFiber(
      UUID fiberId, Map<UUID, BigDecimal> capturedComposition, String standardName) {
    if (fiberId == null || capturedComposition == null || capturedComposition.isEmpty()) {
      throw new IllegalArgumentException("A FIBER target needs the fibre and its composition");
    }
    FiberQualityStandard standard = new FiberQualityStandard();
    standard.targetType = FiberQualityTargetType.FIBER;
    standard.fiberId = fiberId;
    standard.targetComposition = new HashMap<>(FiberComposition.normalize(capturedComposition));
    standard.standardName = standardName;
    standard.isDefault = false;
    return standard;
  }

  /**
   * Applicability of this profile to a fibre with an effective composition (FIBER-CATALOG-1 §8).
   * ISO_CODE: a pure fibre of that ISO at exactly {@code {fibre: 100}}. FIBER: the same fibre and a
   * numerically equal composition. Nothing else applies: no dominant-component fallback.
   */
  public boolean appliesTo(
      UUID fiberId, UUID fiberIsoCodeId, FiberKind kind, Map<UUID, BigDecimal> effective) {
    return switch (targetType) {
      case ISO_CODE ->
          kind == FiberKind.PURE
              && fiberIsoCodeId != null
              && fiberIsoCodeId.equals(getIsoCodeId())
              && FiberComposition.sameComposition(effective, FiberComposition.pure(fiberId));
      case FIBER ->
          this.fiberId != null
              && this.fiberId.equals(fiberId)
              && FiberComposition.sameComposition(targetComposition, effective);
    };
  }

  /** True when at least one of the seven metric groups defines a threshold. */
  public boolean hasAnyCriterion() {
    return java.util.stream.Stream.of(
            finenessMin,
            finenessTarget,
            finenessMax,
            lengthMmMin,
            lengthMmTarget,
            lengthMmMax,
            strengthCndTexMin,
            strengthCndTexTarget,
            strengthCndTexMax,
            elongationPctMin,
            elongationPctTarget,
            elongationPctMax,
            moisturePctMin,
            moisturePctTarget,
            moisturePctMax,
            trashContentPctMin,
            trashContentPctTarget,
            trashContentPctMax,
            uniformityIndexMin,
            uniformityIndexTarget,
            uniformityIndexMax)
        .anyMatch(java.util.Objects::nonNull);
  }

  @Override
  protected String getModuleCode() {
    return "FQST";
  }
}
