package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * The date planning proposes to offer the customer, append-only. It is a proposal, never a promise:
 * it becomes the committed date only when the customer approves the sent version. It keeps the
 * delivery term it was made under; if the order's term or place changes afterwards, the proposal no
 * longer applies and must be made again before the order can go to the customer. It is valid until
 * a set time, after which the planning basis has to be checked again.
 */
@Entity
@Immutable
@Table(name = "delivery_proposal", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryProposal extends BaseEntity {

  public static final int MAX_NOTE_LENGTH = 1000;

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "sequence_no", nullable = false, updatable = false)
  private int sequence;

  /** The hand-over to planning it was made in; a later hand-over does not reuse it. */
  @Column(name = "planning_round", nullable = false, updatable = false)
  private int planningRound;

  /** The evaluation within the round; reopening the evaluation starts a new one. */
  @Column(name = "planning_evaluation", nullable = false, updatable = false)
  private int planningEvaluation;

  @Column(name = "proposed_on", nullable = false, updatable = false)
  private LocalDate proposedOn;

  @Column(name = "valid_until", nullable = false, updatable = false)
  private Instant validUntil;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_term", nullable = false, updatable = false, length = 3)
  private DeliveryTerm deliveryTerm;

  @Column(name = "delivery_place", nullable = false, updatable = false, length = 200)
  private String deliveryPlace;

  @Enumerated(EnumType.STRING)
  @Column(name = "incoterms_version", nullable = false, updatable = false, length = 20)
  private IncotermsVersion incotermsVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_event", nullable = false, updatable = false, length = 40)
  private DeliveryEvent deliveryEvent;

  @Column(name = "note", updatable = false, length = MAX_NOTE_LENGTH)
  private String note;

  @Column(name = "proposed_by", nullable = false, updatable = false)
  private UUID proposedBy;

  @Column(name = "proposed_at", nullable = false, updatable = false)
  private Instant proposedAt;

  /**
   * Records a planner's proposal under the order's current term. The term must be entered (the date
   * has to refer to a known event), the date cannot be in the past and the validity must end after
   * now.
   */
  public static DeliveryProposal propose(
      UUID salesOrderId,
      DeliveryProposal previous,
      int planningRound,
      int planningEvaluation,
      LocalDate proposedOn,
      Instant validUntil,
      DeliveryTerms terms,
      String note,
      UUID proposedBy,
      Instant now,
      LocalDate today) {
    if (salesOrderId == null || proposedBy == null || now == null || today == null) {
      throw new IllegalArgumentException("Order, planner and time are required");
    }
    if (terms == null || !terms.isAgreed()) {
      throw new OrderDomainException(
          "Enter the delivery term first: the proposed date must refer to a known event");
    }
    if (proposedOn == null || proposedOn.isBefore(today)) {
      throw new OrderDomainException("Propose a date from today on");
    }
    if (validUntil == null || !validUntil.isAfter(now)) {
      throw new OrderDomainException("The proposal must stay valid beyond now");
    }
    String text = note == null || note.isBlank() ? null : note.trim();
    if (text != null && text.length() > MAX_NOTE_LENGTH) {
      throw new OrderDomainException("The note is too long");
    }
    DeliveryProposal value = new DeliveryProposal();
    value.salesOrderId = salesOrderId;
    value.sequence = previous == null ? 1 : previous.sequence + 1;
    value.planningRound = planningRound;
    value.planningEvaluation = planningEvaluation;
    value.proposedOn = proposedOn;
    value.validUntil = validUntil;
    value.deliveryTerm = terms.term();
    value.deliveryPlace = terms.place();
    value.incotermsVersion = terms.version();
    value.deliveryEvent = terms.event();
    value.note = text;
    value.proposedBy = proposedBy;
    value.proposedAt = now;
    return value;
  }

  public DeliveryTerms termsOf() {
    return new DeliveryTerms(deliveryTerm, deliveryPlace, incotermsVersion);
  }

  /** Still made under the order's current term and place. */
  public boolean appliesTo(DeliveryTerms current) {
    return termsOf().equals(current);
  }

  /** Made in the order's current hand-over to planning. */
  public boolean belongsToRound(int round) {
    return planningRound == round;
  }

  /** Made before the evaluation was reopened: its basis has to be proposed or confirmed again. */
  public boolean predatesEvaluation(int evaluation) {
    return planningEvaluation < evaluation;
  }

  public boolean isExpiredAt(Instant now) {
    return !validUntil.isAfter(now);
  }

  @Override
  protected String getModuleCode() {
    return "SDP";
  }
}
