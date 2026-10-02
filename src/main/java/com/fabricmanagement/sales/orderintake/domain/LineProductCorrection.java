package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A traced product correction of one order line (SOI K18, R19). Append-only. */
@Entity
@Table(name = "line_product_correction", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineProductCorrection extends BaseEntity {

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "sales_order_line_id", nullable = false, updatable = false)
  private UUID salesOrderLineId;

  @Column(name = "old_product_id", nullable = false, updatable = false)
  private UUID oldProductId;

  @Column(name = "new_product_id", nullable = false, updatable = false)
  private UUID newProductId;

  @Column(name = "line_version", nullable = false, updatable = false)
  private long lineVersion;

  @Column(name = "reason", updatable = false, columnDefinition = "TEXT")
  private String reason;

  @Column(name = "corrected_by", nullable = false, updatable = false)
  private UUID correctedBy;

  @Column(name = "corrected_at", nullable = false, updatable = false)
  private Instant correctedAt;

  public static LineProductCorrection record(
      UUID salesOrderId,
      UUID salesOrderLineId,
      UUID oldProductId,
      UUID newProductId,
      long lineVersion,
      String reason,
      UUID correctedBy,
      Instant correctedAt) {
    if (salesOrderId == null
        || salesOrderLineId == null
        || oldProductId == null
        || newProductId == null
        || correctedBy == null
        || correctedAt == null) {
      throw new IllegalArgumentException("Order, line, products, actor and time are required");
    }
    if (oldProductId.equals(newProductId)) {
      throw new IllegalArgumentException("The new product must differ from the current one");
    }
    LineProductCorrection correction = new LineProductCorrection();
    correction.salesOrderId = salesOrderId;
    correction.salesOrderLineId = salesOrderLineId;
    correction.oldProductId = oldProductId;
    correction.newProductId = newProductId;
    correction.lineVersion = lineVersion;
    correction.reason = reason == null || reason.isBlank() ? null : reason.trim();
    correction.correctedBy = correctedBy;
    correction.correctedAt = correctedAt;
    return correction;
  }

  @Override
  protected String getModuleCode() {
    return "LPC";
  }
}
