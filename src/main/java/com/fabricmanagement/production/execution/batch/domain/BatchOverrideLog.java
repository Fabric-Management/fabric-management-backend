package com.fabricmanagement.production.execution.batch.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * Audit log for manual batch status overrides.
 *
 * <p>Records when a user bypasses normal transition rules (e.g. QC gate). Use case: Supervisor
 * overrides PENDING_QC → AVAILABLE or QC_REJECTED → AVAILABLE without following standard flow.
 *
 * <p>Append-only; no soft delete. Reason is mandatory for traceability.
 */
@Entity
@Table(
    name = "production_execution_batch_override_log",
    schema = "production",
    indexes = {
      @Index(name = "idx_override_log_batch_id", columnList = "batch_id"),
      @Index(name = "idx_override_log_overridden_at", columnList = "overridden_at"),
      @Index(name = "idx_override_log_overridden_by", columnList = "overridden_by")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOverrideLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Column(name = "from_status", nullable = false, length = 50)
  private String fromStatus;

  @Column(name = "to_status", nullable = false, length = 50)
  private String toStatus;

  @Column(name = "overridden_by", nullable = false)
  private UUID overriddenBy;

  @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
  private String reason;

  @Column(name = "overridden_at", nullable = false)
  private Instant overriddenAt;

  /**
   * Create a new override log entry.
   *
   * @param batchId the batch that was overridden
   * @param fromStatus previous status
   * @param toStatus new status (after override)
   * @param overriddenBy user who performed the override
   * @param reason mandatory justification
   * @return new BatchOverrideLog (not persisted)
   */
  public static BatchOverrideLog create(
      UUID batchId, String fromStatus, String toStatus, UUID overriddenBy, String reason) {
    return BatchOverrideLog.builder()
        .batchId(batchId)
        .fromStatus(fromStatus)
        .toStatus(toStatus)
        .overriddenBy(overriddenBy)
        .reason(reason)
        .overriddenAt(Instant.now())
        .build();
  }
}
