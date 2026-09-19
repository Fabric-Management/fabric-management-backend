package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.*;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pure order-wide conservation. Eligibility is supplied by the authoritative production adapter.
 */
public final class OrderCoverEvidenceEvaluator {
  public static final String RULE_VERSION = "ORDER_COVER_EVIDENCE_V2";

  private OrderCoverEvidenceEvaluator() {}

  public static List<Line> evaluate(Requirements requirements, Inputs inputs) {
    Map<UUID, Demand> demands =
        inputs.demands().stream().collect(Collectors.toMap(Demand::lineId, Function.identity()));
    Map<UUID, BigDecimal> remaining = new HashMap<>();
    Map<UUID, Map<UUID, BigDecimal>> proposedByLot = new HashMap<>();
    Set<UUID> seen = new HashSet<>();
    inputs
        .lots()
        .forEach(
            lot -> {
              if (!seen.add(lot.lotId()))
                throw new IllegalArgumentException("Duplicate physical lot");
              if (lot.eligibility() == Eligibility.ELIGIBLE) {
                if (lot.suitableFree() == null || lot.suitableFree().signum() < 0)
                  throw new IllegalArgumentException(
                      "Eligible lot requires non-negative known free stock");
                remaining.put(lot.lotId(), lot.suitableFree());
              }
            });
    Set<UUID> uncertainCompetition = new HashSet<>();
    List<Line> result = new ArrayList<>();
    requirements.lines().stream()
        .sorted(
            Comparator.comparing(
                    Requirement::createdAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(line -> line.lineId().toString()))
        .forEach(
            line ->
                result.add(
                    evaluateLine(
                        line,
                        demands.get(line.lineId()),
                        inputs.lots(),
                        remaining,
                        proposedByLot,
                        uncertainCompetition)));
    return List.copyOf(result);
  }

  private static Line evaluateLine(
      Requirement line,
      Demand demand,
      List<Lot> allLots,
      Map<UUID, BigDecimal> remaining,
      Map<UUID, Map<UUID, BigDecimal>> proposedByLot,
      Set<UUID> uncertainCompetition) {
    List<Lot> lots =
        allLots.stream()
            .filter(lot -> line.productId() != null && line.productId().equals(lot.productId()))
            .sorted(Comparator.comparing(lot -> lot.lotId().toString()))
            .toList();
    List<Source> sources = new ArrayList<>();
    if (line.source() != null) sources.add(line.source());
    if (demand != null) sources.addAll(demand.sources());
    lots.forEach(lot -> sources.addAll(lot.sources()));
    LinkedHashSet<String> reasons = new LinkedHashSet<>();
    lots.forEach(lot -> reasons.addAll(lot.suitabilityFor(line.lineId()).reasons()));
    if (demand != null && demand.reason() != null) reasons.add(demand.reason());
    String unknown = null;
    if (line.productId() == null) unknown = "PRODUCT_REQUIREMENT_MISSING";
    else if (!line.complete())
      unknown =
          line.incompleteReason() == null
              ? "REQUIREMENT_COMPLETENESS_UNKNOWN"
              : line.incompleteReason();
    else if (demand == null || demand.quantity() == null)
      unknown = demand == null ? "PRIMARY_MEASURE_UNKNOWN" : demand.reason();
    else if (lots.stream()
        .anyMatch(lot -> lot.suitabilityFor(line.lineId()).eligibility() == Eligibility.UNKNOWN))
      unknown = "SUITABILITY_EVIDENCE_UNKNOWN";
    else if (lots.stream()
        .filter(lot -> lot.suitabilityFor(line.lineId()).eligibility() == Eligibility.ELIGIBLE)
        .anyMatch(lot -> !java.util.Objects.equals(lot.unit(), demand.unit())))
      unknown = "INCOMPATIBLE_PRIMARY_UNITS";

    Quantity requested =
        demand != null && demand.quantity() != null
            ? Quantity.known(demand.quantity(), demand.unit())
            : line.requested() != null && line.unit() != null && !line.unit().isBlank()
                ? Quantity.known(line.requested(), line.unit())
                : Quantity.unknown(line.unit(), "REQUESTED_QUANTITY_UNKNOWN");
    String unit = demand == null ? line.unit() : demand.unit();
    if (unknown != null) {
      reasons.add(unknown);
      reasons.add("CHECK_MATERIAL_AVAILABILITY");
      if (line.productId() != null) uncertainCompetition.add(line.productId());
      return new Line(
          line.lineId(),
          line.lineVersion(),
          line.productId(),
          requested,
          Quantity.unknown(unit, unknown),
          Quantity.unknown(unit, unknown),
          Quantity.unknown(unit, unknown),
          Suitability.UNKNOWN,
          List.of(),
          List.copyOf(reasons),
          distinctSources(sources),
          List.of(unknown));
    }
    if (demand.quantity().signum() <= 0)
      throw new IllegalArgumentException("Requested quantity must be positive");
    List<Lot> eligible =
        lots.stream()
            .filter(lot -> lot.suitabilityFor(line.lineId()).eligibility() == Eligibility.ELIGIBLE)
            .filter(lot -> lot.suitableFree().signum() > 0)
            .toList();
    BigDecimal suitableFree =
        eligible.stream().map(Lot::suitableFree).reduce(BigDecimal.ZERO, BigDecimal::add);
    Suitability suitability =
        eligible.isEmpty()
            ? Suitability.NO_MATCH
            : eligible.size() > 1 ? Suitability.AMBIGUOUS : Suitability.EXACT;
    List<String> blocking = new ArrayList<>();
    if (suitability == Suitability.AMBIGUOUS) {
      reasons.add("VERIFY_PHYSICAL_STOCK");
      blocking.add("MULTIPLE_ELIGIBLE_LOTS");
    }
    if (suitableFree.signum() > 0 && uncertainCompetition.contains(line.productId())) {
      String reason = "COMPETING_REQUIREMENT_UNKNOWN";
      reasons.add(reason);
      reasons.add("CHECK_MATERIAL_AVAILABILITY");
      blocking.add(reason);
      return new Line(
          line.lineId(),
          line.lineVersion(),
          line.productId(),
          requested,
          Quantity.known(suitableFree, unit),
          Quantity.unknown(unit, reason),
          Quantity.unknown(unit, reason),
          suitability,
          List.of(),
          List.copyOf(reasons),
          distinctSources(sources),
          List.copyOf(blocking));
    }
    BigDecimal remainingSuitableFree =
        eligible.stream()
            .map(lot -> remaining.get(lot.lotId()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    Map<UUID, BigDecimal> priorLines =
        new java.util.TreeMap<>(Comparator.comparing(UUID::toString));
    eligible.forEach(
        lot ->
            proposedByLot
                .getOrDefault(lot.lotId(), Map.of())
                .forEach((id, quantity) -> priorLines.merge(id, quantity, BigDecimal::add)));
    var competing =
        priorLines.entrySet().stream()
            .map(
                entry ->
                    new CompetingAllocation(entry.getKey(), Quantity.known(entry.getValue(), unit)))
            .toList();
    BigDecimal needed = demand.quantity();
    for (Lot lot : eligible) {
      BigDecimal taken = remaining.get(lot.lotId()).min(needed);
      remaining.put(lot.lotId(), remaining.get(lot.lotId()).subtract(taken));
      if (taken.signum() > 0)
        proposedByLot
            .computeIfAbsent(lot.lotId(), ignored -> new HashMap<>())
            .put(line.lineId(), taken);
      needed = needed.subtract(taken);
    }
    if (needed.signum() > 0) {
      reasons.add("CHECK_PRODUCTION_CAPACITY");
      reasons.add("CONFIRM_DELIVERY_DATE");
      blocking.add("INSUFFICIENT_SUITABLE_STOCK");
      if (!competing.isEmpty()) blocking.add("COMPETING_LINE_ALLOCATION");
    }
    if (suitability == Suitability.NO_MATCH) blocking.add("NO_SUITABLE_STOCK");
    return new Line(
        line.lineId(),
        line.lineVersion(),
        line.productId(),
        requested,
        Quantity.known(suitableFree, demand.unit()),
        Quantity.known(remainingSuitableFree, demand.unit()),
        Quantity.known(needed, demand.unit()),
        suitability,
        competing,
        List.copyOf(reasons),
        distinctSources(sources),
        List.copyOf(blocking));
  }

  private static List<Source> distinctSources(List<Source> sources) {
    return sources.stream()
        .distinct()
        .sorted(Comparator.comparing(Source::type).thenComparing(source -> source.id().toString()))
        .toList();
  }
}
