package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * An append-only quantity proposal for one line (SOI K08). Proposing never reserves stock and never
 * changes the line; only a recorded customer acceptance does (SOI D3).
 */
@Entity
@Table(name = "quantity_proposal", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuantityProposal extends BaseEntity {

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "sales_order_line_id", nullable = false, updatable = false)
  private UUID salesOrderLineId;

  @Column(name = "requested_qty", nullable = false, updatable = false, precision = 15, scale = 3)
  private BigDecimal requestedQty;

  @Column(name = "unit", nullable = false, updatable = false, length = 20)
  private String unit;

  @Enumerated(EnumType.STRING)
  @Column(name = "evaluation_status", nullable = false, updatable = false, length = 30)
  private QuantityEvaluationResult.EvaluationStatus evaluationStatus;

  @Column(name = "evidence_fingerprint", nullable = false, updatable = false, length = 64)
  private String evidenceFingerprint;

  @Type(JsonType.class)
  @Column(name = "result", nullable = false, updatable = false, columnDefinition = "jsonb")
  private QuantityEvaluationResult result;

  @Column(name = "evaluated_by", nullable = false, updatable = false)
  private UUID evaluatedBy;

  @Column(name = "evaluated_at", nullable = false, updatable = false)
  private Instant evaluatedAt;

  public static QuantityProposal record(
      UUID salesOrderId,
      UUID salesOrderLineId,
      BigDecimal requestedQty,
      String unit,
      QuantityEvaluationResult result,
      String evidenceFingerprint,
      UUID evaluatedBy,
      Instant evaluatedAt) {
    QuantityProposal proposal = new QuantityProposal();
    proposal.salesOrderId = salesOrderId;
    proposal.salesOrderLineId = salesOrderLineId;
    proposal.requestedQty = requestedQty;
    proposal.unit = unit;
    proposal.result = result;
    proposal.evaluationStatus = result.status();
    proposal.evidenceFingerprint = evidenceFingerprint;
    proposal.evaluatedBy = evaluatedBy;
    proposal.evaluatedAt = evaluatedAt;
    return proposal;
  }

  public Optional<QuantityOption> option(String optionKey) {
    return result.options().stream()
        .filter(option -> option.optionKey().equals(optionKey))
        .findFirst();
  }

  @Override
  protected String getModuleCode() {
    return "QPR";
  }
}
