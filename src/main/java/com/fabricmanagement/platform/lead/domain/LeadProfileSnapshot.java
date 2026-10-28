package com.fabricmanagement.platform.lead.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only retained evidence owned by Lead, without a tenant FK or tenant purge scope. */
@Entity
@Table(name = "lead_profile_snapshot", schema = "common_company")
@Getter
@NoArgsConstructor
public class LeadProfileSnapshot {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "lead_id", nullable = false, updatable = false)
  private UUID leadId;

  @Column(nullable = false, updatable = false)
  private long revision;

  @Column(name = "source", nullable = false, updatable = false)
  private String source;

  @Column(name = "step_id", updatable = false)
  private String stepId;

  @Column(name = "operation_id", updatable = false)
  private UUID operationId;

  @Column(name = "actor_user_id", updatable = false)
  private UUID actorUserId;

  @Column(name = "accepted_at", nullable = false, updatable = false)
  private Instant acceptedAt;

  @Column(name = "completed_at", updatable = false)
  private Instant completedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "evidence", nullable = false, updatable = false, columnDefinition = "jsonb")
  private CompanyEvidence evidence;

  public static LeadProfileSnapshot capture(
      UUID leadId,
      long revision,
      String source,
      String stepId,
      UUID operationId,
      UUID actor,
      CompanyEvidence evidence,
      Instant completedAt) {
    var snapshot = new LeadProfileSnapshot();
    snapshot.leadId = leadId;
    snapshot.revision = revision;
    snapshot.source = source;
    snapshot.stepId = stepId;
    snapshot.operationId = operationId;
    snapshot.actorUserId = actor;
    snapshot.evidence = evidence;
    snapshot.acceptedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    snapshot.completedAt = completedAt;
    return snapshot;
  }
}
