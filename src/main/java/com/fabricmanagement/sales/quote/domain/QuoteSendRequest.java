package com.fabricmanagement.sales.quote.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quote_send_request", schema = "sales")
@Getter
@Setter
@NoArgsConstructor
public class QuoteSendRequest extends BaseEntity {

  @Column(name = "quote_id", nullable = false)
  private UUID quoteId;

  @Column(name = "contact_id", nullable = false)
  private UUID contactId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, length = 20)
  private QuoteApprovalChannel channel = QuoteApprovalChannel.EMAIL;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private QuoteSendRequestStatus status = QuoteSendRequestStatus.PENDING;

  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decision_note", columnDefinition = "TEXT")
  private String decisionNote;

  public static QuoteSendRequest create(
      UUID tenantId,
      UUID quoteId,
      UUID contactId,
      QuoteApprovalChannel channel,
      UUID requestedBy,
      Instant requestedAt) {
    QuoteSendRequest request = new QuoteSendRequest();
    request.setTenantId(tenantId);
    request.quoteId = quoteId;
    request.contactId = contactId;
    request.channel = channel;
    request.requestedBy = requestedBy;
    request.requestedAt = requestedAt;
    request.status = QuoteSendRequestStatus.PENDING;
    return request;
  }

  public void approve(UUID approverId, Instant decidedAt) {
    requirePending();
    this.status = QuoteSendRequestStatus.APPROVED;
    this.decidedBy = approverId;
    this.decidedAt = decidedAt;
  }

  public void reject(UUID approverId, Instant decidedAt, String decisionNote) {
    requirePending();
    if (decisionNote == null || decisionNote.isBlank()) {
      throw SalesDomainException.invalidQuoteSendRequestDecision(
          "decisionNote is required when rejecting a quote send request.");
    }
    this.status = QuoteSendRequestStatus.REJECTED;
    this.decidedBy = approverId;
    this.decidedAt = decidedAt;
    this.decisionNote = decisionNote.strip();
  }

  private void requirePending() {
    if (this.status != QuoteSendRequestStatus.PENDING) {
      throw SalesDomainException.invalidQuoteSendRequestDecision(
          "Quote send request is already " + this.status + ".");
    }
  }

  @Override
  protected String getModuleCode() {
    return "QSR";
  }
}
