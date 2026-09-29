package com.fabricmanagement.sales.orderintake.domain;

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

/** A technical evaluation of a custom request by planning or a manager (SOI K15, R17). */
@Entity
@Table(name = "customer_request_evaluation", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerRequestEvaluation extends BaseEntity {

  @Column(name = "request_id", nullable = false, updatable = false)
  private UUID requestId;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, updatable = false, length = 30)
  private CustomerRequestEvaluationOutcome outcome;

  @Column(name = "note", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String note;

  @Column(name = "evaluated_by", nullable = false, updatable = false)
  private UUID evaluatedBy;

  @Column(name = "evaluated_at", nullable = false, updatable = false)
  private Instant evaluatedAt;

  public static CustomerRequestEvaluation record(
      UUID requestId,
      CustomerRequestEvaluationOutcome outcome,
      String note,
      UUID evaluatedBy,
      Instant evaluatedAt) {
    if (requestId == null || outcome == null || evaluatedBy == null || evaluatedAt == null) {
      throw new IllegalArgumentException("Request, outcome, evaluator and time are required");
    }
    if (note == null || note.isBlank()) {
      throw new IllegalArgumentException("The evaluation needs its reasoning");
    }
    CustomerRequestEvaluation evaluation = new CustomerRequestEvaluation();
    evaluation.requestId = requestId;
    evaluation.outcome = outcome;
    evaluation.note = note.trim();
    evaluation.evaluatedBy = evaluatedBy;
    evaluation.evaluatedAt = evaluatedAt;
    return evaluation;
  }

  @Override
  protected String getModuleCode() {
    return "CRE";
  }
}
