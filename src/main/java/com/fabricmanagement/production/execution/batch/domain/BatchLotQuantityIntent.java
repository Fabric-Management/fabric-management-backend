package com.fabricmanagement.production.execution.batch.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "batch_lot_quantity_intent",
    schema = "production",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_batch_lot_intent_line_batch",
          columnNames = {"tenant_id", "quote_line_id", "batch_id"})
    },
    indexes = {
      @Index(
          name = "idx_batch_lot_intent_tenant_batch_status",
          columnList = "tenant_id, batch_id, status"),
      @Index(
          name = "idx_batch_lot_intent_tenant_line_status",
          columnList = "tenant_id, quote_line_id, status"),
      @Index(
          name = "idx_batch_lot_intent_tenant_expiry_status",
          columnList = "tenant_id, expires_at, status")
    })
@Getter
@NoArgsConstructor
public class BatchLotQuantityIntent extends BaseEntity {

  @Column(name = "quote_id", nullable = false)
  private UUID quoteId;

  @Column(name = "quote_number", length = 50)
  private String quoteNumber;

  @Column(name = "quote_line_id", nullable = false)
  private UUID quoteLineId;

  @Column(name = "marketer_id")
  private UUID marketerId;

  @Column(name = "marketer_name")
  private String marketerName;

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 3)
  private BigDecimal quantity;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private BatchLotQuantityIntentStatus status = BatchLotQuantityIntentStatus.ACTIVE;

  @Column(name = "expires_at")
  private LocalDate expiresAt;

  @Column(name = "released_at")
  private Instant releasedAt;

  public static BatchLotQuantityIntent place(
      UUID tenantId,
      UUID quoteId,
      String quoteNumber,
      UUID quoteLineId,
      UUID marketerId,
      String marketerName,
      UUID batchId,
      BigDecimal quantity,
      String unit,
      LocalDate expiresAt) {
    BatchLotQuantityIntent intent = new BatchLotQuantityIntent();
    intent.setTenantId(tenantId);
    intent.quoteId = quoteId;
    intent.quoteNumber = quoteNumber;
    intent.quoteLineId = quoteLineId;
    intent.marketerId = marketerId;
    intent.marketerName = marketerName;
    intent.batchId = batchId;
    intent.quantity = quantity;
    intent.unit = unit;
    intent.status = BatchLotQuantityIntentStatus.ACTIVE;
    intent.expiresAt = expiresAt;
    intent.onCreate();
    return intent;
  }

  public void update(
      UUID quoteId,
      String quoteNumber,
      UUID marketerId,
      String marketerName,
      BigDecimal quantity,
      String unit,
      LocalDate expiresAt) {
    this.quoteId = quoteId;
    this.quoteNumber = quoteNumber;
    this.marketerId = marketerId;
    this.marketerName = marketerName;
    this.quantity = quantity;
    this.unit = unit;
    this.expiresAt = expiresAt;
    this.status = BatchLotQuantityIntentStatus.ACTIVE;
    this.releasedAt = null;
    onUpdate();
  }

  public void reactivate(
      UUID quoteId,
      String quoteNumber,
      UUID marketerId,
      String marketerName,
      BigDecimal quantity,
      String unit,
      LocalDate expiresAt) {
    update(quoteId, quoteNumber, marketerId, marketerName, quantity, unit, expiresAt);
  }

  /**
   * Aligns the advisory expiry with the quote's current valid-until. Only ACTIVE intents move;
   * returns whether anything changed so callers can skip needless saves.
   */
  public boolean resyncExpiry(LocalDate expiresAt) {
    if (status != BatchLotQuantityIntentStatus.ACTIVE
        || Objects.equals(this.expiresAt, expiresAt)) {
      return false;
    }
    this.expiresAt = expiresAt;
    onUpdate();
    return true;
  }

  public boolean release(Instant releasedAt) {
    if (status == BatchLotQuantityIntentStatus.RELEASED) {
      return false;
    }
    status = BatchLotQuantityIntentStatus.RELEASED;
    this.releasedAt = releasedAt;
    onUpdate();
    return true;
  }

  @Override
  public String getModuleCode() {
    return "LOTINT";
  }
}
