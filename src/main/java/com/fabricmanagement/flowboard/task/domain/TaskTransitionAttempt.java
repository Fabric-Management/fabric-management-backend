package com.fabricmanagement.flowboard.task.domain;

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

/** Durable claim and replay record for a user or system Task action. */
@Entity
@Table(schema = "flowboard", name = "task_transition_attempt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskTransitionAttempt extends BaseEntity {

  @Column(name = "task_id", nullable = false, updatable = false)
  private UUID taskId;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  @Column(name = "idempotency_key", nullable = false, updatable = false, length = 120)
  private String idempotencyKey;

  @Column(name = "action_key", nullable = false, updatable = false, length = 80)
  private String actionKey;

  @Column(name = "payload_fingerprint", nullable = false, updatable = false, length = 64)
  private String payloadFingerprint;

  @Column(name = "expected_version", nullable = false, updatable = false)
  private Long expectedVersion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TaskTransitionOutcome outcome;

  @Column(name = "result_type", length = 80)
  private String resultType;

  @Column(name = "result_id")
  private UUID resultId;

  @Column(name = "rejection_code", length = 80)
  private String rejectionCode;

  @Column(name = "rejection_message", length = 500)
  private String rejectionMessage;

  @Column(name = "completed_at")
  private Instant completedAt;

  /** Creates the same pending claim shape used by the native idempotency boundary. */
  public static TaskTransitionAttempt claim(TaskActionCommand command) {
    var attempt = new TaskTransitionAttempt();
    attempt.taskId = command.taskId();
    attempt.actorId = command.actorId();
    attempt.idempotencyKey = command.idempotencyKey();
    attempt.actionKey = command.actionKey();
    attempt.payloadFingerprint = command.payloadFingerprint();
    attempt.expectedVersion = command.expectedVersion();
    attempt.outcome = TaskTransitionOutcome.PENDING;
    return attempt;
  }

  /** Applies BaseEntity's identity and audit defaults before a native claim insert. */
  public void prepareNativeInsert(UUID tenantId, UUID actorId, Instant claimedAt) {
    if (getId() != null) {
      throw new IllegalStateException("Task transition attempt already has a persistence identity");
    }
    if (!this.actorId.equals(actorId)) {
      throw new IllegalArgumentException(
          "Task transition actor does not match the current auditor");
    }
    setId(UUID.randomUUID());
    setTenantId(tenantId);
    setUid(generateUid());
    setCreatedAt(claimedAt);
    setUpdatedAt(claimedAt);
    setCreatedBy(actorId);
    setUpdatedBy(actorId);
    setIsActive(true);
    setVersion(0L);
  }

  public void completeAccepted(String resultType, UUID resultId, Instant completedAt) {
    requirePending();
    if (resultType == null || resultType.isBlank() || resultId == null || completedAt == null) {
      throw new IllegalArgumentException(
          "Accepted Task transition requires a typed result and time");
    }
    this.outcome = TaskTransitionOutcome.ACCEPTED;
    this.resultType = resultType;
    this.resultId = resultId;
    this.completedAt = completedAt;
  }

  public void completeRejected(String code, String message, Instant completedAt) {
    requirePending();
    if (code == null || code.isBlank() || completedAt == null) {
      throw new IllegalArgumentException("Rejected Task transition requires a code and time");
    }
    this.outcome = TaskTransitionOutcome.REJECTED_BUSINESS;
    this.rejectionCode = code;
    this.rejectionMessage = message;
    this.completedAt = completedAt;
  }

  private void requirePending() {
    if (outcome != TaskTransitionOutcome.PENDING) {
      throw new IllegalStateException("A completed Task transition attempt is immutable");
    }
  }

  @Override
  protected String getModuleCode() {
    return "TATT";
  }
}
