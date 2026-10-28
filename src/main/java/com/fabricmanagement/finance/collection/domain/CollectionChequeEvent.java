package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeState;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Immutable instrument evidence: occurred/effective time and recording time remain distinct. */
@Entity
@Table(name = "collection_cheque_event", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionChequeEvent extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID chequeId;

  /** The cheque's evidence revision when this happened: the execution order of its events. */
  @Column(nullable = false, updatable = false)
  private long evidenceRevision;

  @Column(nullable = false, updatable = false, length = 20)
  private String action;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private ChequeState state;

  @Column(nullable = false, updatable = false)
  private LocalDate effectiveDate;

  @Column(nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false, length = 1000)
  private String reason;

  @Column(nullable = false, updatable = false, length = 1000)
  private String evidence;

  @Column(nullable = false, updatable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false, updatable = false)
  private LocalDate dueOn;

  @Column(nullable = false, updatable = false)
  private LocalDate receivedOn;

  @Column(nullable = false, updatable = false, length = 100)
  private String instrumentReference;

  @Column(updatable = false)
  private UUID paymentId;

  @Override
  protected String getModuleCode() {
    return "CEVT";
  }

  public static CollectionChequeEvent record(
      CollectionCheque cheque,
      String action,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason,
      String evidence) {
    var value = new CollectionChequeEvent();
    value.chequeId = cheque.getId();
    value.action = action;
    value.evidenceRevision = cheque.getEvidenceRevision();
    value.state = cheque.getState();
    value.effectiveDate = effective;
    value.occurredAt = occurred;
    value.actorId = actor;
    value.reason = reason;
    value.evidence = evidence;
    value.amount = cheque.getAmount();
    value.dueOn = cheque.getDueOn();
    value.receivedOn = cheque.getReceivedOn();
    value.instrumentReference = cheque.getInstrumentReference();
    value.paymentId = cheque.getClearedPaymentId();
    return value;
  }
}
