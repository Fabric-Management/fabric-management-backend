package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeState;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
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

  /** PAYMENT-PLAN-7 R14: the classification as it stood after this event; null = not recorded. */
  @Enumerated(EnumType.STRING)
  @Column(updatable = false, length = 10)
  private DrawerKind drawerKind;

  @Column(updatable = false, length = 255)
  private String drawerName;

  @Column(updatable = false)
  private UUID drawerId;

  /** PAYMENT-PLAN-9: the application an APPLY or REVERSE_APPLICATION event is about. */
  @Column(updatable = false)
  private UUID applicationId;

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
    value.drawerKind = cheque.getDrawerKind();
    value.drawerName = cheque.getDrawerName();
    value.drawerId = cheque.getDrawerId();
    return value;
  }

  /**
   * An application or its reversal (PAYMENT-PLAN-9 R3, R3a). It carries the cheque's state as it
   * stands, unchanged, so it never moves the cheque's state at a report cut.
   */
  public static CollectionChequeEvent application(
      CollectionCheque cheque,
      String action,
      UUID application,
      LocalDate effective,
      Instant occurred,
      UUID actor,
      String reason,
      String evidence) {
    if (!"APPLY".equals(action) && !"REVERSE_APPLICATION".equals(action))
      throw new IllegalArgumentException("Not an application event: " + action);
    var value = record(cheque, action, effective, occurred, actor, reason, evidence);
    value.applicationId = application;
    return value;
  }
}
