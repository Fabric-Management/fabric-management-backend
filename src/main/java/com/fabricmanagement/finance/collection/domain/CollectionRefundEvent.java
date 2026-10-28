package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.RefundAction;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One step in a refund's life. {@code step} is its execution order (1, 2, ...) so ties on date or
 * clock precision never decide which event is last. Effective and recording time stay distinct.
 */
@Entity
@Table(name = "collection_refund_event", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionRefundEvent extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID refundId;

  @Column(nullable = false, updatable = false)
  private int step;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 10)
  private RefundAction action;

  @Column(nullable = false, updatable = false)
  private LocalDate effectiveDate;

  @Column(nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false, length = 1000)
  private String reason;

  /** The outbound payment that settled the refund; also kept on the REOPEN it caused. */
  @Column(updatable = false)
  private UUID paymentId;

  @Override
  protected String getModuleCode() {
    return "CRFE";
  }

  public static CollectionRefundEvent record(
      UUID refund,
      int step,
      RefundAction action,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason,
      UUID payment) {
    var value = new CollectionRefundEvent();
    value.refundId = refund;
    value.step = step;
    value.action = action;
    value.effectiveDate = effective;
    value.occurredAt = occurred;
    value.actorId = actor;
    value.reason = reason;
    value.paymentId = payment;
    return value;
  }
}
