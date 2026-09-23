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
import java.util.List;
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
  private List<PersistedLine> storedLines;

  public static OrderCoverEvidence create(
      Requirements requirements,
      Inputs inputs,
      long revision,
      Instant now,
      String fingerprint,
      String ruleVersion,
      List<OrderCoverEvidenceDto.Line> lines) {
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
    result.storedLines = lines.stream().map(PersistedLine::from).toList();
    return result;
  }

  public List<OrderCoverEvidenceDto.Line> getLines() {
    return storedLines.stream().map(PersistedLine::toDto).toList();
  }

  public OrderCoverEvidenceDto toDto() {
    return new OrderCoverEvidenceDto(
        getId(),
        caseId,
        revision,
        orderVersion,
        computedAt,
        inputFingerprint,
        ruleVersion,
        getLines());
  }

  /** Persisted evidence facts deliberately exclude read-time competing-line display metadata. */
  public record PersistedLine(
      UUID lineId,
      long lineVersion,
      UUID productId,
      OrderCoverEvidenceDto.Quantity requested,
      OrderCoverEvidenceDto.Quantity suitableFree,
      OrderCoverEvidenceDto.Quantity remainingSuitableFree,
      OrderCoverEvidenceDto.Quantity shortfall,
      OrderCoverEvidenceDto.Suitability suitability,
      List<PersistedCompetingAllocation> competingAllocations,
      List<String> controlReasons,
      List<OrderCoverEvidenceDto.Source> sources,
      List<String> blockingReasons) {
    static PersistedLine from(OrderCoverEvidenceDto.Line line) {
      return new PersistedLine(
          line.lineId(),
          line.lineVersion(),
          line.productId(),
          line.requested(),
          line.suitableFree(),
          line.remainingSuitableFree(),
          line.shortfall(),
          line.suitability(),
          line.competingAllocations().stream().map(PersistedCompetingAllocation::from).toList(),
          line.controlReasons(),
          line.sources(),
          line.blockingReasons());
    }

    OrderCoverEvidenceDto.Line toDto() {
      return new OrderCoverEvidenceDto.Line(
          lineId,
          lineVersion,
          productId,
          requested,
          suitableFree,
          remainingSuitableFree,
          shortfall,
          suitability,
          competingAllocations.stream().map(PersistedCompetingAllocation::toDto).toList(),
          controlReasons,
          sources,
          blockingReasons);
    }
  }

  public record PersistedCompetingAllocation(UUID lineId, OrderCoverEvidenceDto.Quantity quantity) {
    static PersistedCompetingAllocation from(OrderCoverEvidenceDto.CompetingAllocation allocation) {
      return new PersistedCompetingAllocation(allocation.lineId(), allocation.quantity());
    }

    OrderCoverEvidenceDto.CompetingAllocation toDto() {
      return new OrderCoverEvidenceDto.CompetingAllocation(lineId, quantity);
    }
  }

  @Override
  protected String getModuleCode() {
    return "OCE";
  }
}
