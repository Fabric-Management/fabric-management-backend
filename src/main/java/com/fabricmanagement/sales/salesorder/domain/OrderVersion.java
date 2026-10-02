package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

/**
 * A version of the order as it was sent to the customer, append-only. Its content never changes: an
 * approval is given to exactly this version, and a change to the order makes a new one. The
 * planning round and evaluation it was made from tell whether the order still rests on the same
 * evaluation.
 */
@Entity
@Immutable
@Table(name = "order_version", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderVersion extends BaseEntity {

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  /** 1 for the first version sent, then one more for each. */
  @Column(name = "version_no", nullable = false, updatable = false)
  private int versionNo;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, updatable = false, length = 20)
  private OrderVersionKind kind;

  @Column(name = "planning_round", nullable = false, updatable = false)
  private int planningRound;

  @Column(name = "planning_evaluation", nullable = false, updatable = false)
  private int planningEvaluation;

  /** Planning's proposal the version offers; only a version for approval has one. */
  @Column(name = "delivery_proposal_id", updatable = false)
  private UUID deliveryProposalId;

  @Type(JsonType.class)
  @Column(name = "content", nullable = false, updatable = false, columnDefinition = "jsonb")
  private OrderVersionContent content;

  /** SHA-256 of the content as frozen, so a later reader can tell it was not altered. */
  @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
  private String contentHash;

  @Column(name = "frozen_by", nullable = false, updatable = false)
  private UUID frozenBy;

  @Column(name = "frozen_at", nullable = false, updatable = false)
  private Instant frozenAt;

  /**
   * Freezes the order's content as a new version. A version for approval names planning's proposal
   * it offers; an informational one has none.
   */
  public static OrderVersion freeze(
      UUID salesOrderId,
      OrderVersion previous,
      OrderVersionKind kind,
      int planningRound,
      int planningEvaluation,
      UUID deliveryProposalId,
      OrderVersionContent content,
      String contentHash,
      UUID frozenBy,
      Instant frozenAt) {
    if (salesOrderId == null
        || kind == null
        || content == null
        || contentHash == null
        || frozenBy == null
        || frozenAt == null) {
      throw new IllegalArgumentException("Order, kind, content, hash, actor and time are required");
    }
    if (previous != null && !salesOrderId.equals(previous.salesOrderId)) {
      throw new IllegalArgumentException("A version continues the same order's versions");
    }
    if ((kind == OrderVersionKind.APPROVAL) != (deliveryProposalId != null)) {
      throw new IllegalArgumentException("Only a version for approval offers planning's proposal");
    }
    OrderVersion value = new OrderVersion();
    value.salesOrderId = salesOrderId;
    value.versionNo = previous == null ? 1 : previous.versionNo + 1;
    value.kind = kind;
    value.planningRound = planningRound;
    value.planningEvaluation = planningEvaluation;
    value.deliveryProposalId = deliveryProposalId;
    value.content = content;
    value.contentHash = contentHash;
    value.frozenBy = frozenBy;
    value.frozenAt = frozenAt;
    return value;
  }

  /** Made from the order's current planning round and evaluation. */
  public boolean restsOn(SalesOrder order) {
    return planningRound == order.getPlanningRound()
        && planningEvaluation == order.getPlanningEvaluation();
  }

  @Override
  protected String getModuleCode() {
    return "SOV";
  }
}
