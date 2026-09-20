package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(schema = "sales_ord", name = "order_cover_result")
@Getter
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCoverResult extends BaseEntity {
  @Column(name = "case_id", nullable = false, updatable = false)
  private UUID caseId;

  @Column(name = "case_revision", nullable = false, updatable = false)
  private long caseRevision;

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  @Column(name = "actor_kind", nullable = false, updatable = false, length = 20)
  private String actorKind;

  @Column(name = "policy_key", updatable = false, length = 100)
  private String policyKey;

  @Column(updatable = false, length = 1000)
  private String rationale;

  @Column(updatable = false, length = 1000)
  private String note;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "evidence_id", nullable = false, updatable = false)
  private UUID evidenceId;

  @Column(name = "evidence_revision", nullable = false, updatable = false)
  private long evidenceRevision;

  @Column(name = "supersedes_result_id", updatable = false)
  private UUID supersedesResultId;

  public static OrderCoverResult record(
      UUID tenantId,
      UUID caseId,
      long caseRevision,
      UUID orderId,
      UUID actorId,
      String rationale,
      String note,
      UUID evidenceId,
      long evidenceRevision,
      Instant now) {
    var value = new OrderCoverResult();
    value.setTenantId(tenantId);
    value.caseId = caseId;
    value.caseRevision = caseRevision;
    value.salesOrderId = orderId;
    value.actorId = actorId;
    value.actorKind = "USER";
    value.rationale = rationale;
    value.note = note;
    value.evidenceId = evidenceId;
    value.evidenceRevision = evidenceRevision;
    value.recordedAt = now;
    return value;
  }

  @Override
  protected String getModuleCode() {
    return "OCR";
  }
}
