package com.fabricmanagement.flowboard.decision.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Disposable read copy; caller permissions and bucket membership are deliberately absent. */
@Entity
@Table(
    schema = "flowboard",
    name = "decision_subject_projection",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_decision_subject_projection_case",
            columnNames = {"tenant_id", "case_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DecisionSubjectProjection extends BaseEntity {
  @Column(name = "case_id", nullable = false, updatable = false)
  private UUID caseId;

  @Column(nullable = false, length = 30)
  private String kind;

  @Column(name = "subject_type", nullable = false, length = 40)
  private String subjectType;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Column(name = "subject_number", nullable = false, length = 100)
  private String subjectNumber;

  @Column(name = "order_created_by")
  private UUID orderCreatedBy;

  @Column(name = "task_id")
  private UUID taskId;

  @Column(name = "case_state", nullable = false, length = 30)
  private String caseState;

  @Column(name = "case_revision", nullable = false)
  private long caseRevision;

  @Column(name = "unresolved_line_count", nullable = false)
  private int unresolvedLineCount;

  @Column(name = "case_opened_at", nullable = false)
  private Instant caseOpenedAt;

  @Column(name = "case_closed_at")
  private Instant caseClosedAt;

  @Column(name = "evidence_revision")
  private Long evidenceRevision;

  @Column(name = "verdict_code", nullable = false, length = 30)
  private String verdictCode;

  @Column(name = "projected_at", nullable = false)
  private Instant projectedAt;

  @Column(name = "source_event_id")
  private UUID sourceEventId;

  @Override
  protected String getModuleCode() {
    return "DSP";
  }
}
