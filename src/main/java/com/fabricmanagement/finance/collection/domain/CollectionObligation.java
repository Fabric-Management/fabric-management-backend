package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Kind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
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
@Table(name = "collection_obligation", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionObligation extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID orderId;

  @Column(nullable = false, updatable = false)
  private UUID planId;

  @Column(nullable = false, updatable = false)
  private UUID logicalId;

  @Column(nullable = false, updatable = false)
  private UUID partnerId;

  @Column(nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(nullable = false)
  private UUID revisionId;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Kind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Method method;

  private LocalDate expectedDate;
  private LocalDate chequePaymentDate;

  @Column(nullable = false)
  private boolean awaitsConfirmationDate;

  @Column(nullable = false)
  private boolean cancelled;

  private Instant sourceActivatedAt;
  private UUID responsibleUserId;
  private long financialRevision;

  @Override
  protected String getModuleCode() {
    return "COLL";
  }

  public static CollectionObligation create(
      UUID orderId, UUID planId, UUID logicalId, UUID partnerId, String currency) {
    var value = new CollectionObligation();
    value.orderId = orderId;
    value.planId = planId;
    value.logicalId = logicalId;
    value.partnerId = partnerId;
    value.currency = currency;
    return value;
  }

  public void revise(
      UUID revision,
      BigDecimal amount,
      Kind kind,
      Method method,
      LocalDate date,
      boolean awaitsDate,
      LocalDate chequeDate,
      Instant activatedAt,
      boolean cancelled) {
    this.revisionId = revision;
    this.amount = CollectionAmounts.exact(amount, currency);
    this.kind = kind;
    this.method = method;
    this.expectedDate = date;
    this.awaitsConfirmationDate = awaitsDate;
    this.chequePaymentDate = chequeDate;
    this.sourceActivatedAt = activatedAt;
    this.cancelled = cancelled;
  }

  public void cancel() {
    cancelled = true;
  }

  public void assignResponsible(UUID owner) {
    this.responsibleUserId = owner;
    financialChange();
  }

  public void financialChange() {
    financialRevision++;
  }

  public void requireMatchable(BigDecimal settled, BigDecimal requested) {
    if (cancelled)
      throw CollectionRuleException.conflict(
          "COLLECTION_CANCELLED",
          "This expectation is no longer active; recorded money is retained");
    if (requested.compareTo(CollectionAmounts.outstanding(amount, settled)) > 0)
      throw CollectionRuleException.conflict(
          "COLLECTION_OVERMATCH", "Match exceeds recorded outstanding");
  }
}
