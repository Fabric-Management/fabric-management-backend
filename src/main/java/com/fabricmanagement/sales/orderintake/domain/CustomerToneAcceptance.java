package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * The customer saw a concrete shade difference between the named lots and accepted it (SOI A04-b).
 * It stands in for technical compatibility of those lots for tone only; width, weight, strength,
 * quality release and every other requirement stay untouched. A general "different lots are fine"
 * is not recordable here: the shown sample or evidence is mandatory.
 */
@Entity
@Table(name = "customer_tone_acceptance", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerToneAcceptance extends BaseEntity {

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "sales_order_line_id", nullable = false, updatable = false)
  private UUID salesOrderLineId;

  @Type(JsonType.class)
  @Column(name = "batch_ids", nullable = false, updatable = false, columnDefinition = "jsonb")
  private List<UUID> batchIds = new ArrayList<>();

  @Column(name = "evidence_note", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String evidenceNote;

  @Column(name = "evidence_attachment_id", updatable = false)
  private UUID evidenceAttachmentId;

  @Column(name = "customer_contact", nullable = false, updatable = false, length = 200)
  private String customerContact;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, updatable = false, length = 20)
  private AcceptanceChannel channel;

  @Column(name = "accepted_at", nullable = false, updatable = false)
  private Instant acceptedAt;

  @Column(name = "recorded_by", nullable = false, updatable = false)
  private UUID recordedBy;

  @Column(name = "customer_statement_confirmed", nullable = false, updatable = false)
  private boolean customerStatementConfirmed;

  public static CustomerToneAcceptance record(
      UUID customerId,
      UUID salesOrderLineId,
      Collection<UUID> batchIds,
      String evidenceNote,
      UUID evidenceAttachmentId,
      String customerContact,
      AcceptanceChannel channel,
      Instant acceptedAt,
      boolean customerStatementConfirmed,
      UUID recordedBy) {
    if (!customerStatementConfirmed) {
      throw new IllegalArgumentException(
          "Confirm that the customer accepted the shown tone difference");
    }
    Set<UUID> lots = batchIds == null ? Set.of() : Set.copyOf(batchIds);
    if (salesOrderLineId == null
        || customerId == null
        || recordedBy == null
        || acceptedAt == null
        || channel == null) {
      throw new IllegalArgumentException("Customer, recorder, channel and time are required");
    }
    if (lots.size() < 2) {
      throw new IllegalArgumentException("A tone acceptance names at least two lots");
    }
    if (evidenceNote == null || evidenceNote.isBlank()) {
      throw new IllegalArgumentException("Describe the sample or evidence the customer saw");
    }
    if (customerContact == null || customerContact.isBlank()) {
      throw new IllegalArgumentException("The customer contact who accepted is required");
    }
    CustomerToneAcceptance acceptance = new CustomerToneAcceptance();
    acceptance.customerId = customerId;
    acceptance.salesOrderLineId = salesOrderLineId;
    acceptance.batchIds = lots.stream().sorted().toList();
    acceptance.evidenceNote = evidenceNote.trim();
    acceptance.evidenceAttachmentId = evidenceAttachmentId;
    acceptance.customerContact = customerContact.trim();
    acceptance.channel = channel;
    acceptance.acceptedAt = acceptedAt;
    acceptance.recordedBy = recordedBy;
    acceptance.customerStatementConfirmed = customerStatementConfirmed;
    return acceptance;
  }

  public boolean covers(Collection<UUID> lots) {
    return batchIds.containsAll(lots);
  }

  @Override
  protected String getModuleCode() {
    return "CTA";
  }
}
