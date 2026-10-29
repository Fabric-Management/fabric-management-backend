package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

/**
 * The sender's statement that a payment plan revision lowers collected obligations below the cash
 * finance has already recorded. Append-only: one row per revision, written in the submission's own
 * transaction, so a submission that rolls back leaves no acknowledgement behind.
 */
@Entity
@Immutable
@Table(name = "order_payment_plan_settlement_ack", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPaymentPlanSettlementAck extends BaseEntity {
  /** One obligation the revision lowers below recorded cash, as it stood at submission. */
  public record Shortfall(
      UUID obligationId,
      String currency,
      BigDecimal settled,
      BigDecimal agreed,
      BigDecimal proposed) {}

  @Column(nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(nullable = false, updatable = false)
  private UUID planId;

  @Column(nullable = false, updatable = false)
  private UUID revisionId;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private Instant acknowledgedAt;

  @Column(nullable = false, updatable = false)
  private int obligationCount;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", nullable = false, updatable = false)
  private List<Shortfall> shortfalls;

  public static OrderPaymentPlanSettlementAck create(
      UUID salesOrderId,
      UUID planId,
      UUID revisionId,
      UUID actorId,
      Instant acknowledgedAt,
      List<Shortfall> shortfalls) {
    if (salesOrderId == null
        || planId == null
        || revisionId == null
        || actorId == null
        || acknowledgedAt == null
        || shortfalls == null
        || shortfalls.isEmpty()) {
      throw new IllegalArgumentException(
          "Order, plan, revision, actor, time and shortfalls are required");
    }
    var value = new OrderPaymentPlanSettlementAck();
    value.salesOrderId = salesOrderId;
    value.planId = planId;
    value.revisionId = revisionId;
    value.actorId = actorId;
    value.acknowledgedAt = acknowledgedAt;
    value.shortfalls = List.copyOf(shortfalls);
    value.obligationCount = value.shortfalls.size();
    return value;
  }

  @Override
  protected String getModuleCode() {
    return "SSA";
  }
}
