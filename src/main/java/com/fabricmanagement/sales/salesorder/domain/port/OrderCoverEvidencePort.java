package com.fabricmanagement.sales.salesorder.domain.port;

import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto.Source;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Consumer-owned evidence contract. No production types cross this boundary. */
public interface OrderCoverEvidencePort {
  Inputs inspect(Requirements requirements);

  /**
   * Joins the caller's write transaction. Locks existing production rows only. Settlement must
   * additionally prove phantom/master-data protection before stock enrolment.
   */
  Inputs lockAndInspect(Requirements requirements);

  record Requirement(
      UUID lineId,
      long lineVersion,
      UUID productId,
      Instant createdAt,
      BigDecimal requested,
      String unit,
      boolean complete,
      String incompleteReason,
      String requirementFingerprint,
      Source source) {}

  record Requirements(
      UUID tenantId, UUID orderId, UUID caseId, long orderVersion, List<Requirement> lines) {
    public Requirements {
      lines = List.copyOf(lines);
    }

    public Set<UUID> productIds() {
      return lines.stream()
          .map(Requirement::productId)
          .filter(java.util.Objects::nonNull)
          .collect(java.util.stream.Collectors.toSet());
    }
  }

  record Demand(
      UUID lineId, BigDecimal quantity, String unit, String reason, List<Source> sources) {
    public Demand(UUID lineId, BigDecimal quantity, String unit, String reason) {
      this(lineId, quantity, unit, reason, List.of());
    }

    public Demand {
      sources = List.copyOf(sources);
      java.util.Objects.requireNonNull(lineId);
      if (quantity == null && (reason == null || reason.isBlank()))
        throw new IllegalArgumentException("Unknown demand requires a reason");
      if (quantity != null && (unit == null || unit.isBlank()))
        throw new IllegalArgumentException("Known demand requires a unit");
    }
  }

  enum Eligibility {
    ELIGIBLE,
    EXCLUDED,
    UNKNOWN
  }

  record SourceFact(
      Source source,
      String state,
      BigDecimal quantity,
      String unit,
      BigDecimal secondaryQuantity,
      String secondaryUnit,
      UUID relationId,
      Boolean saleable,
      Integer rank) {}

  enum ComparisonResult {
    MATCH,
    EXCLUDED,
    UNKNOWN
  }

  record Comparison(String dimension, ComparisonResult result, List<Source> sources) {
    public Comparison {
      sources = List.copyOf(sources);
    }
  }

  record Lot(
      UUID lotId,
      UUID productId,
      String unit,
      BigDecimal physicalFree,
      BigDecimal suitableFree,
      Eligibility eligibility,
      List<String> reasons,
      List<Source> sources,
      List<SourceFact> facts,
      List<Comparison> comparisons,
      String sourceFingerprint) {
    public Lot {
      reasons = List.copyOf(reasons);
      sources = List.copyOf(sources);
      facts = List.copyOf(facts);
      comparisons = List.copyOf(comparisons);
    }
  }

  record Inputs(List<Demand> demands, List<Lot> lots) {
    public Inputs {
      demands = List.copyOf(demands);
      lots = List.copyOf(lots);
    }
  }
}
