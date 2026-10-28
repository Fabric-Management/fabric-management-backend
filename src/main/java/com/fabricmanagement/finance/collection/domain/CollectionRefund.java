package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * Cash finance owes back to a customer because an obligation was over-settled. It is a fact that
 * never changes; whether it is still open, paid or cancelled is the last {@link
 * CollectionRefundEvent}.
 */
@Entity
@Table(name = "collection_refund", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionRefund extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID obligationId;

  /** The inbound payment whose money is owed back. */
  @Column(nullable = false, updatable = false)
  private UUID paymentId;

  @Column(nullable = false, updatable = false)
  private UUID partnerId;

  @Column(nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(nullable = false, updatable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, updatable = false)
  private LocalDate effectiveDate;

  @Column(nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false, length = 1000)
  private String reason;

  /**
   * The invoice allocations of {@link #paymentId} that finance released in the same command so this
   * refund could be opened. Empty when the payment had enough money not allocated to any invoice.
   */
  @Type(JsonType.class)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private List<ReleasedAllocation> releasedAllocations;

  /** One invoice allocation that was released; the amount is the exact decimal string. */
  public record ReleasedAllocation(UUID allocationId, UUID invoiceId, String amount) {}

  @Override
  protected String getModuleCode() {
    return "CRFD";
  }

  public static CollectionRefund open(
      CollectionObligation obligation,
      UUID payment,
      BigDecimal amount,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason,
      List<ReleasedAllocation> released) {
    var value = new CollectionRefund();
    value.obligationId = obligation.getId();
    value.paymentId = payment;
    value.partnerId = obligation.getPartnerId();
    value.currency = obligation.getCurrency();
    value.amount = CollectionAmounts.positive(amount, obligation.getCurrency());
    value.effectiveDate = effective;
    value.occurredAt = occurred;
    value.actorId = actor;
    value.reason = reason;
    value.releasedAllocations = List.copyOf(released);
    return value;
  }
}
