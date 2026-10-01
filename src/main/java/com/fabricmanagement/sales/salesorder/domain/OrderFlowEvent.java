package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** One move of an order between flow stages, append-only: who moved it, when and why. */
@Entity
@Immutable
@Table(name = "order_flow_event", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderFlowEvent extends BaseEntity {

  public static final int MAX_REASON_LENGTH = 1000;

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_stage", nullable = false, updatable = false, length = 30)
  private OrderFlowStage fromStage;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_stage", nullable = false, updatable = false, length = 30)
  private OrderFlowStage toStage;

  @Column(name = "reason", updatable = false, length = MAX_REASON_LENGTH)
  private String reason;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  public static OrderFlowEvent of(
      UUID salesOrderId,
      OrderFlowStage from,
      OrderFlowStage to,
      String reason,
      UUID actorId,
      Instant occurredAt) {
    if (salesOrderId == null
        || from == null
        || to == null
        || actorId == null
        || occurredAt == null) {
      throw new IllegalArgumentException("Order, stages, actor and time are required");
    }
    OrderFlowEvent value = new OrderFlowEvent();
    value.salesOrderId = salesOrderId;
    value.fromStage = from;
    value.toStage = to;
    value.reason = reason == null || reason.isBlank() ? null : reason.trim();
    value.actorId = actorId;
    value.occurredAt = occurredAt;
    return value;
  }

  @Override
  protected String getModuleCode() {
    return "OFE";
  }
}
