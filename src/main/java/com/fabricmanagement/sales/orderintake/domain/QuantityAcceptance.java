package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * The stock option chosen for a line and, when it differs from the request, the customer's
 * acceptance of it (SOI D3, K08, A01, A11). Accepting is not reserving: pieces are held only at
 * confirmation (A05). A conditional acceptance names lots whose tone compatibility is not yet
 * confirmed; it is kept as the customer's answer but does not lift the block (IK-13).
 */
@Entity
@Table(name = "quantity_acceptance", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuantityAcceptance extends BaseEntity {

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "sales_order_line_id", nullable = false, updatable = false)
  private UUID salesOrderLineId;

  @Column(name = "proposal_id", nullable = false, updatable = false)
  private UUID proposalId;

  @Column(name = "option_key", nullable = false, updatable = false, length = 80)
  private String optionKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "option_kind", nullable = false, updatable = false, length = 30)
  private QuantityOption.OptionKind optionKind;

  @Enumerated(EnumType.STRING)
  @Column(name = "compatibility", nullable = false, updatable = false, length = 30)
  private QuantityOption.Compatibility compatibility;

  @Column(name = "accepted_qty", nullable = false, updatable = false, precision = 15, scale = 3)
  private BigDecimal acceptedQty;

  @Column(name = "canonical_qty", nullable = false, updatable = false, precision = 19, scale = 6)
  private BigDecimal canonicalQty;

  @Column(name = "customer_statement_confirmed", nullable = false, updatable = false)
  private boolean customerStatementConfirmed;

  @Column(name = "unit", nullable = false, updatable = false, length = 20)
  private String unit;

  @Type(JsonType.class)
  @Column(name = "piece_ids", nullable = false, updatable = false, columnDefinition = "jsonb")
  private List<UUID> pieceIds = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "batch_ids", nullable = false, updatable = false, columnDefinition = "jsonb")
  private List<UUID> batchIds = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "basis", nullable = false, updatable = false, length = 30)
  private QuantityAcceptanceBasis basis;

  @Column(name = "conditional", nullable = false, updatable = false)
  private boolean conditional;

  @Column(name = "remnant_acknowledged", nullable = false, updatable = false)
  private boolean remnantAcknowledged;

  @Enumerated(EnumType.STRING)
  @Column(name = "remaining_need", updatable = false, length = 30)
  private RemainingNeed remainingNeed;

  @Column(name = "remaining_qty", updatable = false, precision = 15, scale = 3)
  private BigDecimal remainingQty;

  @Column(name = "customer_contact", updatable = false, length = 200)
  private String customerContact;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", updatable = false, length = 20)
  private AcceptanceChannel channel;

  @Column(name = "accepted_at", updatable = false)
  private Instant acceptedAt;

  @Column(name = "evidence_note", updatable = false, columnDefinition = "TEXT")
  private String evidenceNote;

  @Column(name = "evidence_attachment_id", updatable = false)
  private UUID evidenceAttachmentId;

  @Column(name = "terms_fingerprint", nullable = false, updatable = false, length = 64)
  private String termsFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private QuantityAcceptanceStatus status;

  @Column(name = "recorded_by", nullable = false, updatable = false)
  private UUID recordedBy;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "idempotency_key", updatable = false, length = 100)
  private String idempotencyKey;

  /** Customer side of the record; required unless the option is exactly the request. */
  public record CustomerEvidence(
      String contact,
      AcceptanceChannel channel,
      Instant acceptedAt,
      String note,
      UUID attachmentId,
      boolean statementConfirmed) {}

  public static QuantityAcceptance record(
      UUID salesOrderId,
      UUID salesOrderLineId,
      UUID proposalId,
      QuantityOption option,
      QuantityOption.Compatibility currentCompatibility,
      BigDecimal acceptedQty,
      String unit,
      QuantityAcceptanceBasis basis,
      boolean remnantAcknowledged,
      RemainingNeed remainingNeed,
      BigDecimal remainingQty,
      CustomerEvidence evidence,
      UUID recordedBy,
      Instant recordedAt,
      String idempotencyKey) {
    if (salesOrderId == null || salesOrderLineId == null || proposalId == null || option == null) {
      throw new IllegalArgumentException("Order, line, proposal and option are required");
    }
    if (acceptedQty == null || acceptedQty.signum() <= 0 || unit == null || unit.isBlank()) {
      throw new IllegalArgumentException("A positive accepted quantity and unit are required");
    }
    if (basis == QuantityAcceptanceBasis.CUSTOMER_ACCEPTED) {
      if (evidence == null
          || evidence.contact() == null
          || evidence.contact().isBlank()
          || evidence.channel() == null
          || evidence.acceptedAt() == null
          || !evidence.statementConfirmed()) {
        throw new IllegalArgumentException(
            "A customer acceptance needs contact, channel, time and the recorder's statement");
      }
    }
    if (option.kind() == QuantityOption.OptionKind.BELOW && remainingNeed == null) {
      throw new IllegalArgumentException(
          "A smaller quantity needs a decision on the remaining need");
    }
    if (option.needsRemnantAcknowledgement() && !remnantAcknowledged) {
      throw new IllegalArgumentException("The single-piece remnant warning must be acknowledged");
    }
    if (recordedBy == null || recordedAt == null) {
      throw new IllegalArgumentException("Recorder and time are required");
    }
    QuantityAcceptance acceptance = new QuantityAcceptance();
    acceptance.salesOrderId = salesOrderId;
    acceptance.salesOrderLineId = salesOrderLineId;
    acceptance.proposalId = proposalId;
    acceptance.optionKey = option.optionKey();
    acceptance.optionKind = option.kind();
    acceptance.compatibility = currentCompatibility;
    acceptance.acceptedQty = acceptedQty;
    acceptance.canonicalQty = option.canonicalQuantity();
    acceptance.unit = unit;
    acceptance.pieceIds = new ArrayList<>(option.pieceIds());
    acceptance.batchIds = new ArrayList<>(option.batchIds());
    acceptance.basis = basis;
    acceptance.conditional =
        currentCompatibility == QuantityOption.Compatibility.PENDING_CONFIRMATION;
    acceptance.remnantAcknowledged = remnantAcknowledged;
    acceptance.remainingNeed = remainingNeed;
    acceptance.remainingQty = remainingQty;
    if (evidence != null) {
      acceptance.customerStatementConfirmed = evidence.statementConfirmed();
      acceptance.customerContact = blankToNull(evidence.contact());
      acceptance.channel = evidence.channel();
      acceptance.acceptedAt = evidence.acceptedAt();
      acceptance.evidenceNote = blankToNull(evidence.note());
      acceptance.evidenceAttachmentId = evidence.attachmentId();
    }
    acceptance.status = QuantityAcceptanceStatus.ACTIVE;
    acceptance.recordedBy = recordedBy;
    acceptance.recordedAt = recordedAt;
    acceptance.idempotencyKey = blankToNull(idempotencyKey);
    return acceptance;
  }

  /** Captures the line terms after the acceptance has been applied to the line. */
  public void coverTerms(String fingerprint) {
    if (termsFingerprint != null) {
      throw new IllegalStateException("The covered terms are already fixed");
    }
    this.termsFingerprint = fingerprint;
  }

  public boolean covers(String currentTermsFingerprint) {
    return status == QuantityAcceptanceStatus.ACTIVE
        && termsFingerprint != null
        && termsFingerprint.equals(currentTermsFingerprint);
  }

  public void supersede(Instant at) {
    close(QuantityAcceptanceStatus.SUPERSEDED, at);
  }

  public void withdraw(Instant at) {
    close(QuantityAcceptanceStatus.WITHDRAWN, at);
  }

  public boolean isActiveAcceptance() {
    return status == QuantityAcceptanceStatus.ACTIVE;
  }

  private void close(QuantityAcceptanceStatus target, Instant at) {
    if (status != QuantityAcceptanceStatus.ACTIVE) {
      throw new IllegalStateException("Only an active acceptance can be closed");
    }
    this.status = target;
    this.closedAt = at;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  @Override
  protected String getModuleCode() {
    return "QAC";
  }
}
