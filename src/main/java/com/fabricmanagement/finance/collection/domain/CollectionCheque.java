package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeAction;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeState;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "collection_cheque", schema = "finance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionCheque extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID partnerId;

  @Column(nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Column(nullable = false)
  private LocalDate receivedOn;

  @Column(nullable = false)
  private LocalDate dueOn;

  @Column(nullable = false, length = 100)
  private String instrumentReference;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ChequeState state;

  private long evidenceRevision;
  private UUID clearedPaymentId;

  @Column(length = 1000)
  private String lastEvidence;

  /**
   * PAYMENT-PLAN-7 R14: kind and drawer as recorded at receipt or by an explicit classification
   * correction. All null on cheques from before tagging (not recorded, never guessed).
   */
  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private DrawerKind drawerKind;

  /** Up to 255: an own cheque records the customer's name, which may be that long. */
  @Column(length = 255)
  private String drawerName;

  private UUID drawerId;

  @Override
  protected String getModuleCode() {
    return "CHQ";
  }

  public static CollectionCheque receive(
      UUID partner,
      String currency,
      BigDecimal amount,
      LocalDate received,
      LocalDate due,
      String reference,
      String evidence,
      Drawer drawer) {
    var value = new CollectionCheque();
    value.classify(drawer);
    value.partnerId = partner;
    value.currency = currency;
    value.amount = CollectionAmounts.positive(amount, currency);
    value.receivedOn = received;
    value.dueOn = due;
    value.instrumentReference = reference;
    value.lastEvidence = evidence;
    value.state = ChequeState.RECEIVED;
    return value;
  }

  public void transition(ChequeAction action, UUID paymentId, String evidence) {
    ChequeState next = state.next(action);
    if (action == ChequeAction.PRESENT && state == ChequeState.DISHONOURED) clearedPaymentId = null;
    if (action == ChequeAction.CLEAR && paymentId == null)
      throw CollectionRuleException.invalid("Clearing requires a recorded payment");
    if (action == ChequeAction.CLEAR) clearedPaymentId = paymentId;
    state = next;
    lastEvidence = evidence;
    evidenceRevision++;
  }

  /** Who drew the cheque. A kind is always paired with a name; only an own drawer has an id. */
  public record Drawer(DrawerKind kind, UUID id, String name) {
    public Drawer {
      if (kind == null || name == null || name.isBlank())
        throw CollectionRuleException.invalid("A cheque drawer needs a kind and a name");
      if (kind == DrawerKind.ENDORSED && id != null)
        throw CollectionRuleException.invalid("An endorsed cheque's drawer is not a listed one");
    }
  }

  /** The recorded classification, or empty when it was never recorded. */
  public java.util.Optional<Drawer> drawer() {
    return drawerKind == null
        ? java.util.Optional.empty()
        : java.util.Optional.of(new Drawer(drawerKind, drawerId, drawerName));
  }

  private void classify(Drawer drawer) {
    drawerKind = drawer.kind();
    drawerId = drawer.id();
    drawerName = drawer.name();
  }

  public void correct(
      BigDecimal amount,
      LocalDate received,
      LocalDate due,
      String reference,
      String evidence,
      Drawer drawer) {
    state.next(ChequeAction.CORRECT);
    if (drawer != null) classify(drawer);
    this.amount = CollectionAmounts.positive(amount, currency);
    this.receivedOn = received;
    this.dueOn = due;
    this.instrumentReference = reference;
    this.lastEvidence = evidence;
    evidenceRevision++;
  }
}
