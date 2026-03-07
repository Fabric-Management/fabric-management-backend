package com.fabricmanagement.production.quality.result.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * FiberTestResult - Laboratory test results for fiber batches.
 *
 * <p><b>Purpose:</b> Stores actual measurement values from laboratory tests or production quality
 * checks.
 *
 * <p>Each test result belongs to a specific FiberBatch and records the physical properties
 * measured.
 *
 * <p><b>Domain Separation:</b>
 *
 * <ul>
 *   <li>Fiber = Catalog definition (what type of fiber)
 *   <li>FiberBatch = Production lot (physical inventory)
 *   <li>FiberTestResult = Laboratory measurements (test data)
 * </ul>
 */
@Entity
@Table(
    name = "production_quality_fiber_test_result",
    schema = "production",
    indexes = {
      @Index(name = "idx_fiber_test_batch", columnList = "fiber_batch_id"),
      @Index(name = "idx_fiber_test_date", columnList = "test_date"),
      @Index(name = "idx_fiber_test_tenant", columnList = "tenant_id"),
      @Index(name = "idx_fiber_test_approval", columnList = "approval_status")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberTestResult extends BaseEntity {

  @Column(name = "fiber_batch_id", nullable = false)
  private UUID fiberBatchId;

  @Column(name = "test_date", nullable = false)
  private Instant testDate;

  @Column(name = "test_type", length = 50)
  @Builder.Default
  private String testType = "LABORATORY"; // LABORATORY, PRODUCTION, INCOMING

  // ── Core "Big 4" measurements ───────────────────────────────────────────────

  @Column(name = "fineness")
  private Double fineness;

  @Column(name = "length_mm")
  private Double lengthMm;

  @Column(name = "strength_cn_dtex")
  private Double strengthCndTex;

  @Column(name = "elongation_percent")
  private Double elongationPercent;

  // ── Extended measurements (textile-industry essentials) ────────────────────

  /** Moisture / humidity percentage — critical for weight-based raw material pricing. */
  @Column(name = "moisture_percent")
  private Double moisturePercent;

  /** Trash & neps content percentage — affects waste ratio and yarn quality. */
  @Column(name = "trash_content_percent")
  private Double trashContentPercent;

  // ── Quality gate ──────────────────────────────────────────────────────────

  /** Quality engineer's approval decision. Defaults to PENDING until reviewed. */
  @Enumerated(EnumType.STRING)
  @Column(name = "approval_status", nullable = false, length = 30)
  @Builder.Default
  private TestApprovalStatus approvalStatus = TestApprovalStatus.PENDING;

  // ── Metadata ──────────────────────────────────────────────────────────────

  @Column(name = "test_lab", length = 255)
  private String testLab;

  @Column(name = "test_standard", length = 100)
  private String testStandard;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  @Override
  protected String getModuleCode() {
    return "FTR";
  }
}
