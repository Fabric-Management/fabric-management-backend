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
      Profile profile,
      Source source) {
    public Requirement(
        UUID lineId,
        long lineVersion,
        UUID productId,
        Instant createdAt,
        BigDecimal requested,
        String unit,
        boolean complete,
        String incompleteReason,
        String requirementFingerprint,
        Source source) {
      this(
          lineId,
          lineVersion,
          productId,
          createdAt,
          requested,
          unit,
          complete,
          incompleteReason,
          requirementFingerprint,
          null,
          source);
    }
  }

  enum FacetKind {
    CERTIFICATION,
    ORIGIN,
    COLOUR_IDENTITY,
    SHADE_APPROVAL,
    WIDTH,
    WEIGHT,
    YARN_COUNT,
    YARN_TWIST,
    YARN_CONSTRUCTION,
    FIBRE_GRADE,
    FIBRE_SHADE
  }

  enum FacetState {
    BOUNDED,
    UNCONSTRAINED,
    UNSPECIFIED
  }

  enum FacetRule {
    EXACT,
    MINIMUM,
    MAXIMUM,
    RANGE,
    ALL,
    ANY,
    SET_MEMBERSHIP,
    NONE
  }

  record CertificateRequirement(String scheme, String certificateKind) {
    public CertificateRequirement {
      java.util.Objects.requireNonNull(scheme);
      java.util.Objects.requireNonNull(certificateKind);
    }
  }

  record Facet(
      String identity,
      FacetKind kind,
      FacetState state,
      FacetRule rule,
      UUID colourId,
      List<CertificateRequirement> certificates,
      Set<String> categories) {
    public Facet {
      java.util.Objects.requireNonNull(identity);
      java.util.Objects.requireNonNull(kind);
      java.util.Objects.requireNonNull(state);
      java.util.Objects.requireNonNull(rule);
      certificates = certificates == null ? List.of() : List.copyOf(certificates);
      categories =
          categories == null
              ? Set.of()
              : java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(categories));
      if (state == FacetState.BOUNDED && kind == FacetKind.COLOUR_IDENTITY && colourId == null) {
        throw new IllegalArgumentException("Bounded colour identity requires a colour id");
      }
      if (state == FacetState.BOUNDED
          && kind == FacetKind.CERTIFICATION
          && certificates.isEmpty()) {
        throw new IllegalArgumentException("Bounded certification requires certificate kinds");
      }
      if (state == FacetState.BOUNDED
          && (kind == FacetKind.FIBRE_GRADE || kind == FacetKind.FIBRE_SHADE)
          && categories.isEmpty()) {
        throw new IllegalArgumentException("Bounded fibre category requires accepted values");
      }
    }
  }

  record Profile(boolean complete, List<Facet> facets, List<String> unsupportedConstraints) {
    public Profile {
      facets = facets == null ? List.of() : List.copyOf(facets);
      unsupportedConstraints =
          unsupportedConstraints == null ? List.of() : List.copyOf(unsupportedConstraints);
    }
  }

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

  record LineSuitability(
      UUID lineId, Eligibility eligibility, List<String> reasons, List<Comparison> comparisons) {
    public LineSuitability {
      java.util.Objects.requireNonNull(lineId);
      java.util.Objects.requireNonNull(eligibility);
      reasons = List.copyOf(reasons);
      comparisons = List.copyOf(comparisons);
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
      List<LineSuitability> lineSuitabilities,
      String sourceFingerprint) {
    public Lot {
      reasons = List.copyOf(reasons);
      sources = List.copyOf(sources);
      facts = List.copyOf(facts);
      comparisons = List.copyOf(comparisons);
      lineSuitabilities = lineSuitabilities == null ? List.of() : List.copyOf(lineSuitabilities);
    }

    public Lot(
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
      this(
          lotId,
          productId,
          unit,
          physicalFree,
          suitableFree,
          eligibility,
          reasons,
          sources,
          facts,
          comparisons,
          List.of(),
          sourceFingerprint);
    }

    public LineSuitability suitabilityFor(UUID lineId) {
      return lineSuitabilities.stream()
          .filter(row -> row.lineId().equals(lineId))
          .findFirst()
          .orElse(new LineSuitability(lineId, eligibility, reasons, comparisons));
    }
  }

  record Inputs(List<Demand> demands, List<Lot> lots) {
    public Inputs {
      demands = List.copyOf(demands);
      lots = List.copyOf(lots);
    }
  }
}
