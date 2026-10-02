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
 * One delivery promise agreed with the buyer, in an append-only history per order. The first record
 * is kept for ever, so the original promise can always be compared with the current one. Each
 * record carries the delivery term it was given under (the event the date refers to), who asked for
 * the change and why, and how the buyer agreed. An internal planning revision is not a commitment:
 * only a record agreed with the buyer changes the order's committed date.
 */
@Entity
@Immutable
@Table(name = "delivery_commitment", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryCommitment extends BaseEntity {

  public static final int MAX_REASON_LENGTH = 1000;
  public static final int MAX_CONTACT_LENGTH = 200;

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  /** 1 for the first promise, then one more for each change. */
  @Column(name = "sequence_no", nullable = false, updatable = false)
  private int sequence;

  @Column(name = "previous_commitment_id", updatable = false)
  private UUID previousCommitmentId;

  @Column(name = "committed_on", nullable = false, updatable = false)
  private LocalDate committedOn;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_term", nullable = false, updatable = false, length = 3)
  private DeliveryTerm deliveryTerm;

  @Column(name = "delivery_place", nullable = false, updatable = false, length = 200)
  private String deliveryPlace;

  @Enumerated(EnumType.STRING)
  @Column(name = "incoterms_version", nullable = false, updatable = false, length = 20)
  private IncotermsVersion incotermsVersion;

  /** Derived from the term when recorded and kept, so the history never reinterprets a date. */
  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_event", nullable = false, updatable = false, length = 40)
  private DeliveryEvent deliveryEvent;

  @Enumerated(EnumType.STRING)
  @Column(name = "origin", nullable = false, updatable = false, length = 20)
  private CommitmentChangeOrigin origin;

  @Column(name = "reason", updatable = false, length = MAX_REASON_LENGTH)
  private String reason;

  @Column(name = "customer_contact", nullable = false, updatable = false, length = 200)
  private String customerContact;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, updatable = false, length = 20)
  private CommitmentChannel channel;

  @Column(name = "agreed_at", nullable = false, updatable = false)
  private Instant agreedAt;

  @Column(name = "recorded_by", nullable = false, updatable = false)
  private UUID recordedBy;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  /** What the buyer agreed to; the caller has validated the terms ({@link DeliveryTerms#of}). */
  public record Agreement(
      LocalDate committedOn,
      DeliveryTerms terms,
      CommitmentChangeOrigin origin,
      String reason,
      String customerContact,
      CommitmentChannel channel,
      Instant agreedAt) {}

  /**
   * Records the first promise ({@code previous} null) or a change to {@code previous}. The first
   * promise has origin INITIAL; a change names who asked for it and why, and must change the date
   * or the term. The buyer's agreement (contact, channel, time) is required for every record.
   */
  public static DeliveryCommitment record(
      UUID salesOrderId,
      DeliveryCommitment previous,
      Agreement agreement,
      UUID recordedBy,
      Instant recordedAt) {
    if (salesOrderId == null || agreement == null || recordedBy == null || recordedAt == null) {
      throw new IllegalArgumentException("Order, agreement, recorder and time are required");
    }
    if (previous != null && !salesOrderId.equals(previous.salesOrderId)) {
      throw new IllegalArgumentException("A change continues the same order's history");
    }
    if (agreement.committedOn() == null) {
      throw new OrderDomainException("Choose the committed date");
    }
    DeliveryTerms terms = agreement.terms();
    if (terms == null || !terms.isAgreed()) {
      throw new OrderDomainException(
          "Agree the delivery term first: the committed date must refer to a known event");
    }
    CommitmentChangeOrigin origin = agreement.origin();
    String reason = trimmed(agreement.reason());
    if (previous == null) {
      if (origin != null && origin != CommitmentChangeOrigin.INITIAL) {
        throw new OrderDomainException("The first commitment is the initial promise");
      }
      origin = CommitmentChangeOrigin.INITIAL;
    } else {
      if (origin == null || origin == CommitmentChangeOrigin.INITIAL) {
        throw new OrderDomainException("Say who asked for the change: the buyer or the seller");
      }
      if (reason == null) {
        throw new OrderDomainException("A changed commitment needs its reason");
      }
      if (previous.committedOn.equals(agreement.committedOn())
          && previous.termsOf().equals(terms)) {
        throw new OrderDomainException("Nothing changed: the date and the term are the same");
      }
    }
    if (reason != null && reason.length() > MAX_REASON_LENGTH) {
      throw new OrderDomainException("The reason is too long");
    }
    String contact = trimmed(agreement.customerContact());
    if (contact == null || agreement.channel() == null || agreement.agreedAt() == null) {
      throw new OrderDomainException(
          "Record how the buyer agreed: who, through which channel and when");
    }
    if (contact.length() > MAX_CONTACT_LENGTH) {
      throw new OrderDomainException("The contact is too long");
    }
    if (agreement.agreedAt().isAfter(recordedAt)) {
      throw new OrderDomainException("The agreement cannot be later than now");
    }
    DeliveryCommitment value = new DeliveryCommitment();
    value.salesOrderId = salesOrderId;
    value.sequence = previous == null ? 1 : previous.sequence + 1;
    value.previousCommitmentId = previous == null ? null : previous.getId();
    value.committedOn = agreement.committedOn();
    value.deliveryTerm = terms.term();
    value.deliveryPlace = terms.place();
    value.incotermsVersion = terms.version();
    value.deliveryEvent = terms.event();
    value.origin = origin;
    value.reason = reason;
    value.customerContact = contact;
    value.channel = agreement.channel();
    value.agreedAt = agreement.agreedAt();
    value.recordedBy = recordedBy;
    value.recordedAt = recordedAt;
    return value;
  }

  /** The delivery term this promise was given under. */
  public DeliveryTerms termsOf() {
    return new DeliveryTerms(deliveryTerm, deliveryPlace, incotermsVersion);
  }

  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  @Override
  protected String getModuleCode() {
    return "SDC";
  }
}
