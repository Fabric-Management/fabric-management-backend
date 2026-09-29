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
 * A solution presented to the customer for a custom request: the product (existing, new card or
 * equivalent) and the counter-sample (SOI K16). Immutable content; a new revision supersedes it.
 */
@Entity
@Table(name = "customer_request_revision", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerRequestRevision extends BaseEntity {

  @Column(name = "request_id", nullable = false, updatable = false)
  private UUID requestId;

  @Column(name = "revision_no", nullable = false, updatable = false)
  private int revisionNo;

  @Enumerated(EnumType.STRING)
  @Column(name = "solution", nullable = false, updatable = false, length = 30)
  private CustomerRequestEvaluationOutcome solution;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "summary", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String summary;

  @Column(name = "counter_sample_note", updatable = false, columnDefinition = "TEXT")
  private String counterSampleNote;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CustomerRequestRevisionStatus status;

  @Column(name = "proposed_by", nullable = false, updatable = false)
  private UUID proposedBy;

  @Column(name = "proposed_at", nullable = false, updatable = false)
  private Instant proposedAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  public static CustomerRequestRevision propose(
      UUID requestId,
      int revisionNo,
      CustomerRequestEvaluationOutcome solution,
      UUID productId,
      String summary,
      String counterSampleNote,
      UUID proposedBy,
      Instant proposedAt) {
    if (requestId == null || revisionNo < 1 || proposedBy == null || proposedAt == null) {
      throw new IllegalArgumentException(
          "Request, revision number, proposer and time are required");
    }
    if (solution == null || !solution.hasSolution()) {
      throw new IllegalArgumentException(
          "A revision presents an existing, new or equivalent product");
    }
    if (productId == null) {
      throw new IllegalArgumentException("A revision names the product it proposes");
    }
    if (summary == null || summary.isBlank()) {
      throw new IllegalArgumentException("A revision needs a summary the customer can judge");
    }
    CustomerRequestRevision revision = new CustomerRequestRevision();
    revision.requestId = requestId;
    revision.revisionNo = revisionNo;
    revision.solution = solution;
    revision.productId = productId;
    revision.summary = summary.trim();
    revision.counterSampleNote =
        counterSampleNote == null || counterSampleNote.isBlank() ? null : counterSampleNote.trim();
    revision.status = CustomerRequestRevisionStatus.PROPOSED;
    revision.proposedBy = proposedBy;
    revision.proposedAt = proposedAt;
    return revision;
  }

  public void markSent(Instant at) {
    if (status != CustomerRequestRevisionStatus.PROPOSED) {
      throw new IllegalStateException("Only a proposed revision is sent");
    }
    this.status = CustomerRequestRevisionStatus.SENT;
    this.sentAt = at;
  }

  public void decided(CustomerRequestDecisionOutcome outcome) {
    if (status == CustomerRequestRevisionStatus.SUPERSEDED) {
      throw new IllegalStateException("A superseded revision cannot be decided");
    }
    if (status == CustomerRequestRevisionStatus.REJECTED) {
      throw new IllegalStateException("A rejected revision needs a new revision");
    }
    this.status =
        outcome == CustomerRequestDecisionOutcome.APPROVED
            ? CustomerRequestRevisionStatus.APPROVED
            : CustomerRequestRevisionStatus.REJECTED;
  }

  public void supersede() {
    if (status != CustomerRequestRevisionStatus.SUPERSEDED) {
      this.status = CustomerRequestRevisionStatus.SUPERSEDED;
    }
  }

  @Override
  protected String getModuleCode() {
    return "CRV";
  }
}
