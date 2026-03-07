package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * Target quality specifications (LSL / Target / USL) for a fiber.
 *
 * <p>Each fiber can have multiple named specification profiles:
 *
 * <ul>
 *   <li>"Standard" — default internal tolerances
 *   <li>"Premium Export" — tighter tolerances for export markets
 *   <li>"Customer Zara" — customer-specific requirements
 * </ul>
 *
 * <p>The {@code isDefault} flag marks which profile is used for automated pass/fail comparison
 * against {@code FiberTestResult} actuals. Only one default per fiber per tenant.
 *
 * <p>Each parameter uses a min/target/max triplet (tolerance band):
 *
 * <pre>
 *   ├── min (LSL) ──── target (nominal) ──── max (USL) ──┤
 * </pre>
 */
@Entity
@Table(
    name = "prod_fiber_specification",
    schema = "production",
    indexes = {
      @Index(name = "idx_fiber_spec_tenant", columnList = "tenant_id"),
      @Index(name = "idx_fiber_spec_fiber", columnList = "fiber_id")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_fiber_spec_name",
          columnNames = {"tenant_id", "fiber_id", "spec_name"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberSpecification extends BaseEntity {

  @Column(name = "fiber_id", nullable = false)
  private UUID fiberId;

  @Column(name = "spec_name", nullable = false, length = 100)
  private String specName;

  @Column(name = "is_default", nullable = false)
  @Builder.Default
  private Boolean isDefault = false;

  @Column(name = "test_standard", length = 100)
  private String testStandard;

  // ── Fineness (micronaire / dtex) ──────────────────────────────────────────

  @Column(name = "fineness_min")
  private Double finenessMin;

  @Column(name = "fineness_target")
  private Double finenessTarget;

  @Column(name = "fineness_max")
  private Double finenessMax;

  // ── Staple length (mm) ────────────────────────────────────────────────────

  @Column(name = "length_min")
  private Double lengthMin;

  @Column(name = "length_target")
  private Double lengthTarget;

  @Column(name = "length_max")
  private Double lengthMax;

  // ── Tenacity / Strength (cN/dtex) ─────────────────────────────────────────

  @Column(name = "strength_min")
  private Double strengthMin;

  @Column(name = "strength_target")
  private Double strengthTarget;

  @Column(name = "strength_max")
  private Double strengthMax;

  // ── Elongation at break (%) ───────────────────────────────────────────────

  @Column(name = "elongation_min")
  private Double elongationMin;

  @Column(name = "elongation_target")
  private Double elongationTarget;

  @Column(name = "elongation_max")
  private Double elongationMax;

  // ── Moisture / humidity (%) ───────────────────────────────────────────────

  @Column(name = "moisture_min")
  private Double moistureMin;

  @Column(name = "moisture_target")
  private Double moistureTarget;

  @Column(name = "moisture_max")
  private Double moistureMax;

  // ── Trash & neps content (%) ──────────────────────────────────────────────

  @Column(name = "trash_content_min")
  private Double trashContentMin;

  @Column(name = "trash_content_target")
  private Double trashContentTarget;

  @Column(name = "trash_content_max")
  private Double trashContentMax;

  // ── Metadata ──────────────────────────────────────────────────────────────

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  @Override
  protected String getModuleCode() {
    return "FSPEC";
  }
}
