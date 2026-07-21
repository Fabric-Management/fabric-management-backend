package com.fabricmanagement.production.quality.decision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/** Append-only quality decision ledger entry. Corrections are represented by new decisions. */
@Entity
@Table(name = "quality_decision", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QualityDecision implements Persistable<UUID> {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "decision_scope", nullable = false, updatable = false, length = 30)
  private QualityDecisionScope decisionScope;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, updatable = false, length = 30)
  private QualityDecisionOutcome outcome;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", updatable = false, length = 50)
  private QualityReasonCode reasonCode;

  @Column(name = "remarks", updatable = false, columnDefinition = "TEXT")
  private String remarks;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "origin", nullable = false, updatable = false, length = 40)
  private QualityDecisionOrigin origin;

  @Column(name = "source_event_id", updatable = false)
  private UUID sourceEventId;

  @Column(name = "supersedes_decision_id", updatable = false)
  private UUID supersedesDecisionId;

  @Column(name = "decided_at", nullable = false, updatable = false)
  private Instant decidedAt;

  @Column(name = "seq", nullable = false, updatable = false)
  private long seq;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Override
  public UUID getId() {
    return id;
  }

  /** Ledger entries are insert-only; Spring Data must never select/merge by the assigned UUID. */
  @Override
  @Transient
  public boolean isNew() {
    return true;
  }

  public static QualityDecision create(
      UUID tenantId,
      UUID batchId,
      QualityDecisionScope decisionScope,
      QualityDecisionOutcome outcome,
      QualityReasonCode reasonCode,
      String remarks,
      UUID actorId,
      QualityDecisionOrigin origin,
      UUID sourceEventId,
      UUID supersedesDecisionId,
      long seq,
      Instant decidedAt) {
    QualityDecision decision = new QualityDecision();
    decision.id = UUID.randomUUID();
    decision.tenantId = tenantId;
    decision.batchId = batchId;
    decision.decisionScope = decisionScope;
    decision.outcome = outcome;
    decision.reasonCode = reasonCode;
    decision.remarks = remarks;
    decision.actorId = actorId;
    decision.origin = origin;
    decision.sourceEventId = sourceEventId;
    decision.supersedesDecisionId = supersedesDecisionId;
    decision.seq = seq;
    decision.decidedAt = decidedAt;
    decision.createdAt = decidedAt;
    return decision;
  }
}
