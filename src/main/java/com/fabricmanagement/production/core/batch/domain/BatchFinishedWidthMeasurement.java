package com.fabricmanagement.production.core.batch.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A measured finished width of a lot (SOI IK-08, limited LOT-EVIDENCE-1). Append-only: a new
 * measurement supersedes the previous one; nothing is overwritten. The width is a measurement, not
 * the nominal greige width and not a derivation from it (SOI K11).
 */
@Entity
@Table(name = "batch_finished_width_measurement", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchFinishedWidthMeasurement extends BaseEntity {

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Column(name = "width_value", nullable = false, updatable = false, precision = 8, scale = 2)
  private BigDecimal widthValue;

  @Column(name = "width_unit", nullable = false, updatable = false, length = 10)
  private String widthUnit;

  @Column(name = "method_note", updatable = false, columnDefinition = "TEXT")
  private String methodNote;

  @Column(name = "measured_by", nullable = false, updatable = false)
  private UUID measuredBy;

  @Column(name = "measured_at", nullable = false, updatable = false)
  private Instant measuredAt;

  public static BatchFinishedWidthMeasurement record(
      UUID batchId,
      BigDecimal widthValue,
      String widthUnit,
      String methodNote,
      UUID measuredBy,
      Instant measuredAt) {
    if (batchId == null || measuredBy == null || measuredAt == null) {
      throw new IllegalArgumentException("Batch, measurer and time are required");
    }
    if (widthValue == null || widthValue.signum() <= 0) {
      throw new IllegalArgumentException("Measured width must be positive");
    }
    if (widthValue.stripTrailingZeros().scale() > 2) {
      throw new IllegalArgumentException("Measured width allows at most two decimals");
    }
    String unit = widthUnit == null ? null : widthUnit.trim().toUpperCase(Locale.ROOT);
    if (!"CM".equals(unit) && !"IN".equals(unit)) {
      throw new IllegalArgumentException("Width unit must be CM or IN");
    }
    BatchFinishedWidthMeasurement measurement = new BatchFinishedWidthMeasurement();
    measurement.batchId = batchId;
    measurement.widthValue = widthValue.setScale(2, RoundingMode.UNNECESSARY);
    measurement.widthUnit = unit;
    measurement.methodNote = methodNote == null || methodNote.isBlank() ? null : methodNote.trim();
    measurement.measuredBy = measuredBy;
    measurement.measuredAt = measuredAt;
    return measurement;
  }

  @Override
  protected String getModuleCode() {
    return "BFW";
  }
}
