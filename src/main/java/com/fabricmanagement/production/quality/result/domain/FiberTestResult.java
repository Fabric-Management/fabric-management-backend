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
      @Index(name = "idx_fiber_test_tenant", columnList = "tenant_id")
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

  /** Laboratory measurement values. */
  @Column(name = "fineness")
  private Double fineness;

  @Column(name = "length_mm")
  private Double lengthMm;

  @Column(name = "strength_cn_dtex")
  private Double strengthCndTex;

  @Column(name = "elongation_percent")
  private Double elongationPercent;

  @Column(name = "test_lab", length = 255)
  private String testLab;

  @Column(name = "test_standard", length = 100)
  private String testStandard; // ISO 1833, ASTM D7641, etc.

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  @Override
  protected String getModuleCode() {
    return "FTR";
  }
}
