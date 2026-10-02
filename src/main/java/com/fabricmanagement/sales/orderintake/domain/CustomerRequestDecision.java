package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The customer's answer to a revision, recorded by an authorised salesperson (SOI A11): contact,
 * channel and time are required, evidence is optional, and the recorder states that the answer came
 * from the customer. It covers the terms fingerprinted at the time.
 */
@Entity
@Table(name = "customer_request_decision", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerRequestDecision extends BaseEntity {

  @Column(name = "request_id", nullable = false, updatable = false)
  private UUID requestId;

  @Column(name = "revision_id", nullable = false, updatable = false)
  private UUID revisionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, updatable = false, length = 20)
  private CustomerRequestDecisionOutcome outcome;

  @Column(name = "terms_fingerprint", nullable = false, updatable = false, length = 64)
  private String termsFingerprint;

  @Column(name = "customer_contact", nullable = false, updatable = false, length = 200)
  private String customerContact;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, updatable = false, length = 20)
  private AcceptanceChannel channel;

  @Column(name = "decided_at", nullable = false, updatable = false)
  private Instant decidedAt;

  @Column(name = "note", updatable = false, columnDefinition = "TEXT")
  private String note;

  @Column(name = "evidence_attachment_id", updatable = false)
  private UUID evidenceAttachmentId;

  @Column(name = "recorded_by", nullable = false, updatable = false)
  private UUID recordedBy;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "customer_statement_confirmed", nullable = false, updatable = false)
  private boolean customerStatementConfirmed;

  public static CustomerRequestDecision record(
      UUID requestId,
      UUID revisionId,
      CustomerRequestDecisionOutcome outcome,
      String termsFingerprint,
      String customerContact,
      AcceptanceChannel channel,
      Instant decidedAt,
      String note,
      UUID evidenceAttachmentId,
      boolean statementConfirmed,
      UUID recordedBy,
      Instant recordedAt) {
    if (requestId == null || revisionId == null || outcome == null || termsFingerprint == null) {
      throw new IllegalArgumentException("Request, revision, outcome and terms are required");
    }
    if (customerContact == null
        || customerContact.isBlank()
        || channel == null
        || decidedAt == null) {
      throw new IllegalArgumentException("Customer contact, channel and time are required (A11)");
    }
    if (!statementConfirmed) {
      throw new IllegalArgumentException("Confirm that the answer came from the customer (A11)");
    }
    if (recordedBy == null || recordedAt == null) {
      throw new IllegalArgumentException("Recorder and time are required");
    }
    CustomerRequestDecision decision = new CustomerRequestDecision();
    decision.requestId = requestId;
    decision.revisionId = revisionId;
    decision.outcome = outcome;
    decision.termsFingerprint = termsFingerprint;
    decision.customerContact = customerContact.trim();
    decision.channel = channel;
    decision.decidedAt = decidedAt;
    decision.note = note == null || note.isBlank() ? null : note.trim();
    decision.evidenceAttachmentId = evidenceAttachmentId;
    decision.customerStatementConfirmed = statementConfirmed;
    decision.recordedBy = recordedBy;
    decision.recordedAt = recordedAt;
    return decision;
  }

  @Override
  protected String getModuleCode() {
    return "CRD";
  }
}
