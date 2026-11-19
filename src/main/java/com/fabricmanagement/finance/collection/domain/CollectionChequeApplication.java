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

/**
 * Evidence that part of a cheque covers an obligation (PAYMENT-PLAN-9 K2). Like a match, it keeps
 * when it took effect, when it was recorded ({@code createdAt}), who recorded it and why; a wrong
 * one is reversed once, never edited or deleted. Current coverage counts unreversed rows only;
 * reports count the rows in force at their effective and recording cuts. One cheque may hold
 * several active applications to the same obligation.
 */
@Entity
@Table(name = "collection_cheque_application", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionChequeApplication extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID chequeId;

  @Column(nullable = false, updatable = false)
  private UUID obligationId;

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

  private LocalDate reversedOn;
  private Instant reversedAt;
  private Instant reversalOccurredAt;
  private UUID reversedBy;

  @Column(length = 1000)
  private String reversalReason;

  @Override
  protected String getModuleCode() {
    return "CAPL";
  }

  public static CollectionChequeApplication create(
      UUID cheque,
      UUID obligation,
      BigDecimal amount,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason) {
    var value = new CollectionChequeApplication();
    value.chequeId = cheque;
    value.obligationId = obligation;
    value.amount = amount;
    value.effectiveDate = effective;
    value.occurredAt = occurred;
    value.actorId = actor;
    value.reason = reason;
    return value;
  }

  /** True once its reversal is recorded; it then no longer covers anything currently. */
  public boolean isReversed() {
    return reversedAt != null;
  }

  /**
   * Records that this application was wrong (PAYMENT-PLAN-9 R3a), once. {@code recorded} must come
   * from the same clock as {@code createdAt}, the application's own recording time, because reports
   * compare both against one knowledge cutoff; it is never stored earlier than {@code createdAt}.
   */
  public void reverse(
      LocalDate effective, Instant occurred, Instant recorded, UUID actor, String reason) {
    if (reversedAt != null)
      throw CollectionRuleException.conflict(
          "CHEQUE_APPLICATION_ALREADY_REVERSED", "This cheque application is already reversed");
    if (effective.isBefore(effectiveDate))
      throw CollectionRuleException.invalid(
          "An application cannot be reversed before it took effect");
    reversedOn = effective;
    reversalOccurredAt = occurred;
    reversedAt = recorded.isBefore(getCreatedAt()) ? getCreatedAt() : recorded;
    reversedBy = actor;
    reversalReason = reason;
  }
}
