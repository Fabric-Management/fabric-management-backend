package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "collection_payment_match", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionPaymentMatch extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID obligationId;

  @Column(nullable = false, updatable = false)
  private UUID paymentId;

  @Column(updatable = false)
  private UUID chequeId;

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

  private Instant paymentInvalidatedAt;
  private Instant invalidationRecordedAt;
  private LocalDate reversedOn;
  private Instant reversedAt;
  private Instant reversalOccurredAt;
  private UUID reversedBy;

  @Column(length = 1000)
  private String reversalReason;

  @Override
  protected String getModuleCode() {
    return "CMAT";
  }

  public static CollectionPaymentMatch create(
      UUID obligation,
      UUID payment,
      UUID cheque,
      BigDecimal amount,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason) {
    var value = new CollectionPaymentMatch();
    value.obligationId = obligation;
    value.paymentId = payment;
    value.chequeId = cheque;
    value.amount = amount;
    value.effectiveDate = effective;
    value.occurredAt = occurred;
    value.actorId = actor;
    value.reason = reason;
    return value;
  }

  public void observeVoidedPayment(Instant sourceAt, Instant recordedAt) {
    if (paymentInvalidatedAt == null) {
      paymentInvalidatedAt = sourceAt;
      invalidationRecordedAt = recordedAt;
    }
  }

  public void reverse(
      LocalDate effective, Instant occurred, Instant recorded, UUID actor, String reason) {
    if (reversedAt != null)
      throw CollectionRuleException.conflict(
          "COLLECTION_MATCH_ALREADY_REVERSED", "The match has already been reversed");
    if (effective.isBefore(effectiveDate))
      throw CollectionRuleException.dateBeforeMatch(effectiveDate);
    reversedOn = effective;
    reversalOccurredAt = occurred;
    reversedAt = recorded;
    reversedBy = actor;
    reversalReason = reason;
  }
}
