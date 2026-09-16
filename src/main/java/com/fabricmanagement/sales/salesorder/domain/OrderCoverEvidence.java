package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.Inputs;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.Requirements;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

/** Append-only derived snapshot. Corrections append; historical evidence stays addressable. */
@Entity
@Table(name = "order_cover_evidence", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
public class OrderCoverEvidence extends BaseEntity {
  @Column(name = "case_id", nullable = false, updatable = false)
  private UUID caseId;

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "revision", nullable = false, updatable = false)
  private long revision;

  @Column(name = "order_version", nullable = false, updatable = false)
  private long orderVersion;

  @Column(name = "computed_at", nullable = false, updatable = false)
  private Instant computedAt;

  @Column(name = "input_fingerprint", nullable = false, updatable = false, length = 64)
  private String inputFingerprint;

  @Column(name = "rule_version", nullable = false, updatable = false, length = 60)
  private String ruleVersion;

  @Type(JsonType.class)
  @Column(name = "requirements", nullable = false, updatable = false, columnDefinition = "jsonb")
  private Requirements requirements;

  @Type(JsonType.class)
  @Column(name = "inputs", nullable = false, updatable = false, columnDefinition = "jsonb")
  private Inputs inputs;

  @Type(JsonType.class)
  @Column(name = "lines", nullable = false, updatable = false, columnDefinition = "jsonb")
  private java.util.List<OrderCoverEvidenceDto.Line> lines;

  public static OrderCoverEvidence create(
      Requirements requirements,
      Inputs inputs,
      long revision,
      Instant now,
      String fingerprint,
      String ruleVersion,
      java.util.List<OrderCoverEvidenceDto.Line> lines) {
    var result = new OrderCoverEvidence();
    result.setTenantId(requirements.tenantId());
    result.caseId = requirements.caseId();
    result.salesOrderId = requirements.orderId();
    result.revision = revision;
    result.orderVersion = requirements.orderVersion();
    result.computedAt = now;
    result.inputFingerprint = fingerprint;
    result.ruleVersion = ruleVersion;
    result.requirements = requirements;
    result.inputs = inputs;
    result.lines = java.util.List.copyOf(lines);
    return result;
  }

  public OrderCoverEvidenceDto toDto() {
    return new OrderCoverEvidenceDto(
        getId(), caseId, revision, orderVersion, computedAt, inputFingerprint, ruleVersion, lines);
  }

  @Override
  protected String getModuleCode() {
    return "OCE";
  }
}
