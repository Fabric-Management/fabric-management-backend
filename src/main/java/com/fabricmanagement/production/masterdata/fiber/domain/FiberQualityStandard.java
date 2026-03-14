package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import jakarta.persistence.*;
import lombok.*;

/**
 * Quality standards (LSL / Target / USL) per ISO code.
 *
 * <p>Each ISO code can have multiple named standard profiles:
 *
 * <ul>
 *   <li>"Standard" — default internal tolerances
 *   <li>"Premium" — tighter tolerances for export markets
 *   <li>"Export" — customer-specific requirements
 * </ul>
 *
 * <p>The {@code isDefault} flag marks which profile is used for automated pass/fail comparison
 * against {@code FiberTestResult} actuals. Only one default per iso_code per tenant.
 *
 * <p>Design: ISO code based (not fiber_id) — enables shared standards across fibers of the same
 * type.
 */
@Entity
@Table(
    name = "prod_fiber_quality_standard",
    schema = "production",
    indexes = {
      @Index(name = "idx_fiber_quality_standard_tenant", columnList = "tenant_id"),
      @Index(name = "idx_fiber_quality_standard_iso_code", columnList = "iso_code_id")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_fiber_quality_standard_tenant_iso_name",
          columnNames = {"tenant_id", "iso_code_id", "standard_name"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberQualityStandard extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "iso_code_id", nullable = false)
  private FiberIsoCode isoCode;

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

  @Override
  protected String getModuleCode() {
    return "FQST";
  }
}
