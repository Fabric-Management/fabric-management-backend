package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult.EvaluationStatus;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.Compatibility;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.OptionKind;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns production stock evidence into whole-piece quantity options (SOI R08–R11, A01–A04-b, A06,
 * IK-07). Pure: no persistence, no clock, no security. The caller converts units.
 */
public final class QuantityEvaluator {

  /** Search resolution: 0.001 of the canonical unit (millimetre or gram). */
  static final BigDecimal UNITS_PER_CANONICAL = new BigDecimal("1000");

  private QuantityEvaluator() {}

  /** Everything the evaluation needs, already in the canonical unit. */
  public record Input(
      BigDecimal requestedQuantity,
      String unit,
      String canonicalUnit,
      BigDecimal canonicalTarget,
      Function<BigDecimal, BigDecimal> toLineUnit,
      List<ProposalLot> lots,
      List<Set<UUID>> confirmedGroups,
      List<Set<UUID>> toneAcceptedGroups,
      boolean singleLotRequired,
      BigDecimal agreedToleranceUpPct,
      BigDecimal agreedToleranceDownPct) {}

  public record Evaluation(QuantityEvaluationResult result, String evidenceFingerprint) {}

  public static Evaluation evaluate(Input input) {
    List<ProposalLot> lots = orderedLots(input.lots());
    boolean aged = lots.stream().allMatch(lot -> lot.productionDate() != null) && !lots.isEmpty();
    Map<UUID, ProposalLot> lotsById =
        lots.stream().collect(Collectors.toMap(ProposalLot::batchId, Function.identity()));
    int eligible = 0;
    int unknown = 0;
    int excluded = 0;
    Set<String> unknownReasons = new TreeSet<>();
    for (ProposalLot lot : lots) {
      for (ProposalPiece piece : lot.pieces()) {
        switch (piece.state()) {
          case ELIGIBLE -> eligible++;
          case UNKNOWN -> {
            unknown++;
            unknownReasons.add(piece.reason());
          }
          case EXCLUDED -> excluded++;
        }
      }
    }
    String fingerprint = fingerprint(input, lots);
    boolean precise =
        representable(input.canonicalTarget())
            && lots.stream()
                .flatMap(lot -> lot.pieces().stream())
                .filter(piece -> piece.state() == PieceState.ELIGIBLE)
                .allMatch(piece -> representable(piece.canonicalMeasure()));
    if (!precise) {
      return new Evaluation(
          result(
              input,
              EvaluationStatus.UNKNOWN,
              List.of(),
              eligible,
              unknown,
              excluded,
              List.of("QUANTITY_BELOW_RESOLUTION"),
              aged,
              0),
          fingerprint);
    }
    long target = toUnits(input.canonicalTarget());
    if (target <= 0) {
      return new Evaluation(
          result(
              input,
              EvaluationStatus.UNKNOWN,
              List.of(),
              eligible,
              unknown,
              excluded,
              List.of("QUANTITY_BELOW_RESOLUTION"),
              aged,
              0),
          fingerprint);
    }

    List<Run> runs = runs(input, lots);
    Map<UUID, WholePieceSearch.Lot> searchLots = new LinkedHashMap<>();
    for (ProposalLot lot : lots) {
      List<WholePieceSearch.Piece> pieces =
          lot.pieces().stream()
              .filter(piece -> piece.state() == PieceState.ELIGIBLE)
              .map(
                  piece ->
                      new WholePieceSearch.Piece(
                          piece.stockUnitId(), toUnits(piece.canonicalMeasure())))
              .filter(piece -> piece.units() > 0)
              .toList();
      searchLots.put(lot.batchId(), new WholePieceSearch.Lot(lot.batchId(), pieces));
    }

    Candidate exact = null;
    Candidate above = null;
    Candidate below = null;
    Candidate remnant = null;
    boolean limitExceeded = false;
    List<Candidate> pending = new ArrayList<>();
    for (Run run : runs) {
      List<WholePieceSearch.Lot> runLots =
          run.lots().stream().map(searchLots::get).filter(Objects::nonNull).toList();
      WholePieceSearch.Result strict = WholePieceSearch.search(runLots, target, false);
      limitExceeded |= strict.limitExceeded();
      if (run.compatibility() == Compatibility.PENDING_CONFIRMATION) {
        strict.exact().map(s -> new Candidate(OptionKind.EXACT, run, s)).ifPresent(pending::add);
        strict.above().map(s -> new Candidate(OptionKind.ABOVE, run, s)).ifPresent(pending::add);
        strict.below().map(s -> new Candidate(OptionKind.BELOW, run, s)).ifPresent(pending::add);
        continue;
      }
      exact = better(exact, strict.exact().map(s -> new Candidate(OptionKind.EXACT, run, s)), 0);
      above = better(above, strict.above().map(s -> new Candidate(OptionKind.ABOVE, run, s)), 1);
      below = better(below, strict.below().map(s -> new Candidate(OptionKind.BELOW, run, s)), -1);
      WholePieceSearch.Result relaxed = WholePieceSearch.search(runLots, target, true);
      remnant =
          better(
              remnant,
              relaxed
                  .exact()
                  .filter(WholePieceSearch.Selection::leavesSingleRemnant)
                  .map(s -> new Candidate(OptionKind.REQUESTED_WITH_REMNANT, run, s)),
              0);
    }

    List<Candidate> chosen = new ArrayList<>();
    if (exact != null) {
      chosen.add(exact);
    } else {
      if (above != null) chosen.add(above);
      if (below != null) chosen.add(below);
      if (remnant != null) chosen.add(remnant);
    }
    for (Candidate candidate : pending) {
      if (candidate.selection().lotCount() < 2 || coveredByDefiniteGroup(candidate, runs)) {
        continue;
      }
      Candidate definite =
          switch (candidate.kind()) {
            case EXACT -> exact;
            case ABOVE -> exact != null ? exact : above;
            case BELOW -> exact != null ? exact : below;
            default -> null;
          };
      if (definite == null || closer(candidate, definite, target)) {
        chosen.add(candidate);
      }
    }

    List<QuantityOption> computed =
        chosen.stream()
            .map(candidate -> toOption(candidate, input, lotsById, target))
            .distinct()
            .toList();
    // A03: an agreed customer tolerance is applied, not merely displayed.
    List<QuantityOption> options =
        computed.stream().filter(option -> !option.outsideAgreedTolerance()).toList();
    int withheld = computed.size() - options.size();
    EvaluationStatus status;
    if (exact != null) {
      status = EvaluationStatus.EXACT;
    } else if (!options.isEmpty() || withheld > 0) {
      status = EvaluationStatus.OPTIONS;
    } else if (unknown > 0 || limitExceeded) {
      status = EvaluationStatus.UNKNOWN;
    } else {
      status = EvaluationStatus.NO_ELIGIBLE_STOCK;
    }
    if (limitExceeded) {
      unknownReasons.add("EVALUATION_LIMIT");
    }
    return new Evaluation(
        result(
            input,
            status,
            options,
            eligible,
            unknown,
            excluded,
            List.copyOf(unknownReasons),
            aged,
            withheld),
        fingerprint);
  }

  /** A02-b: age only when every candidate lot has a production date; otherwise identity only. */
  static List<ProposalLot> orderedLots(List<ProposalLot> lots) {
    boolean allDated = lots.stream().allMatch(lot -> lot.productionDate() != null);
    Comparator<ProposalLot> byId = Comparator.comparing(ProposalLot::batchId);
    Comparator<ProposalLot> order =
        allDated ? Comparator.comparing(ProposalLot::productionDate).thenComparing(byId) : byId;
    return lots.stream().sorted(order).toList();
  }

  private record Run(List<UUID> lots, Compatibility compatibility, int order) {}

  private record Candidate(OptionKind kind, Run run, WholePieceSearch.Selection selection) {}

  private static List<Run> runs(Input input, List<ProposalLot> lots) {
    List<Run> runs = new ArrayList<>();
    List<UUID> ordered = lots.stream().map(ProposalLot::batchId).toList();
    for (UUID lot : ordered) {
      runs.add(new Run(List.of(lot), Compatibility.SINGLE_LOT, runs.size()));
    }
    if (input.singleLotRequired() || ordered.size() < 2) {
      return runs;
    }
    for (Set<UUID> group : input.confirmedGroups()) {
      List<UUID> members = ordered.stream().filter(group::contains).toList();
      if (members.size() >= 2) {
        runs.add(new Run(members, Compatibility.CONFIRMED, runs.size()));
      }
    }
    for (Set<UUID> group : input.toneAcceptedGroups()) {
      List<UUID> members = ordered.stream().filter(group::contains).toList();
      if (members.size() >= 2) {
        runs.add(new Run(members, Compatibility.TONE_ACCEPTED, runs.size()));
      }
    }
    runs.add(new Run(ordered, Compatibility.PENDING_CONFIRMATION, runs.size()));
    return runs;
  }

  /**
   * Keeps the better candidate. direction 1: smaller total wins (above), -1: larger total wins
   * (below), 0: same target (exact). Ties: fewer lots, then earlier run (older lots first).
   */
  private static Candidate better(Candidate current, Optional<Candidate> offered, int direction) {
    if (offered.isEmpty()) {
      return current;
    }
    Candidate next = offered.get();
    if (current == null) {
      return next;
    }
    long a = current.selection().total();
    long b = next.selection().total();
    if (a != b) {
      boolean nextWins = direction > 0 ? b < a : direction < 0 && b > a;
      return nextWins ? next : current;
    }
    if (next.selection().lotCount() != current.selection().lotCount()) {
      return next.selection().lotCount() < current.selection().lotCount() ? next : current;
    }
    return next.run().order() < current.run().order() ? next : current;
  }

  private static boolean closer(Candidate pending, Candidate definite, long target) {
    return Math.abs(pending.selection().total() - target)
        < Math.abs(definite.selection().total() - target);
  }

  private static boolean coveredByDefiniteGroup(Candidate candidate, List<Run> runs) {
    Set<UUID> used =
        candidate.selection().takes().stream()
            .map(WholePieceSearch.LotTake::lotId)
            .collect(Collectors.toSet());
    return runs.stream()
        .filter(run -> run.compatibility() != Compatibility.PENDING_CONFIRMATION)
        .anyMatch(run -> run.lots().containsAll(used));
  }

  private static QuantityOption toOption(
      Candidate candidate, Input input, Map<UUID, ProposalLot> lotsById, long target) {
    List<QuantityOption.LotPart> parts = new ArrayList<>();
    BigDecimal canonical = BigDecimal.ZERO;
    for (WholePieceSearch.LotTake take : candidate.selection().takes()) {
      ProposalLot lot = lotsById.get(take.lotId());
      Map<UUID, ProposalPiece> pieces =
          lot.pieces().stream()
              .collect(Collectors.toMap(ProposalPiece::stockUnitId, Function.identity()));
      BigDecimal lotCanonical =
          take.pieceIds().stream()
              .map(id -> pieces.get(id).canonicalMeasure())
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      canonical = canonical.add(lotCanonical);
      int unknownInLot =
          (int) lot.pieces().stream().filter(piece -> piece.state() == PieceState.UNKNOWN).count();
      ProposalPiece remnantPiece =
          take.remainingPieces() == 1
              ? lot.pieces().stream()
                  .filter(piece -> piece.state() == PieceState.ELIGIBLE)
                  .filter(piece -> !take.pieceIds().contains(piece.stockUnitId()))
                  .findFirst()
                  .orElse(null)
              : null;
      parts.add(
          new QuantityOption.LotPart(
              lot.batchId(),
              lot.lotNo(),
              take.pieceIds(),
              input.toLineUnit().apply(lotCanonical),
              take.remainingPieces(),
              unknownInLot,
              take.remainingPieces() == 1,
              remnantPiece == null ? null : remnantPiece.stockUnitId(),
              remnantPiece == null
                  ? null
                  : input.toLineUnit().apply(remnantPiece.canonicalMeasure())));
    }
    BigDecimal quantity = input.toLineUnit().apply(canonical);
    BigDecimal difference = quantity.subtract(input.requestedQuantity());
    BigDecimal percent =
        difference
            .multiply(new BigDecimal("100"))
            .divide(input.requestedQuantity(), 1, RoundingMode.HALF_UP);
    boolean outside = outsideTolerance(candidate.kind(), difference, input);
    return new QuantityOption(
        optionKey(candidate),
        candidate.kind(),
        candidate.run().compatibility(),
        quantity,
        canonical,
        difference,
        percent,
        outside,
        parts);
  }

  private static boolean outsideTolerance(OptionKind kind, BigDecimal difference, Input input) {
    if (kind == OptionKind.ABOVE && input.agreedToleranceUpPct() != null) {
      return difference
              .multiply(new BigDecimal("100"))
              .compareTo(input.requestedQuantity().multiply(input.agreedToleranceUpPct()))
          > 0;
    }
    if (kind == OptionKind.BELOW && input.agreedToleranceDownPct() != null) {
      return difference
              .negate()
              .multiply(new BigDecimal("100"))
              .compareTo(input.requestedQuantity().multiply(input.agreedToleranceDownPct()))
          > 0;
    }
    return false;
  }

  static String optionKey(Candidate candidate) {
    String pieces =
        candidate.selection().takes().stream()
            .flatMap(take -> take.pieceIds().stream())
            .map(UUID::toString)
            .sorted()
            .collect(Collectors.joining(","));
    return candidate.kind().name() + ":" + sha256(pieces).substring(0, 16);
  }

  private static QuantityEvaluationResult result(
      Input input,
      EvaluationStatus status,
      List<QuantityOption> options,
      int eligible,
      int unknown,
      int excluded,
      List<String> reasons,
      boolean aged,
      int withheldOutsideTolerance) {
    return new QuantityEvaluationResult(
        status,
        input.requestedQuantity(),
        input.unit(),
        input.canonicalUnit(),
        options,
        eligible,
        unknown,
        excluded,
        reasons,
        aged,
        withheldOutsideTolerance);
  }

  private static boolean representable(BigDecimal quantity) {
    if (quantity == null || quantity.signum() <= 0) return false;
    try {
      toUnits(quantity);
      return true;
    } catch (ArithmeticException e) {
      return false;
    }
  }

  static long toUnits(BigDecimal canonical) {
    if (canonical == null) {
      return 0;
    }
    return canonical.multiply(UNITS_PER_CANONICAL).longValueExact();
  }

  /** Fingerprint of every fact the options depend on; a changed piece makes an acceptance stale. */
  static String fingerprint(Input input, List<ProposalLot> lots) {
    StringBuilder text = new StringBuilder();
    text.append(input.canonicalTarget().stripTrailingZeros().toPlainString()).append('|');
    text.append(input.singleLotRequired()).append('|');
    lots.forEach(
        lot -> {
          text.append(lot.batchId()).append('#').append(lot.widthEvidence()).append(';');
          lot.pieces().stream()
              .sorted(Comparator.comparing(ProposalPiece::stockUnitId))
              .forEach(
                  piece ->
                      text.append(piece.stockUnitId())
                          .append(':')
                          .append(piece.version())
                          .append(':')
                          .append(piece.state())
                          .append(':')
                          .append(
                              piece.canonicalMeasure() == null
                                  ? "-"
                                  : piece.canonicalMeasure().stripTrailingZeros().toPlainString())
                          .append(';'));
        });
    input.confirmedGroups().stream()
        .map(QuantityEvaluator::sortedKey)
        .sorted()
        .forEach(key -> text.append("C").append(key));
    input.toneAcceptedGroups().stream()
        .map(QuantityEvaluator::sortedKey)
        .sorted()
        .forEach(key -> text.append("T").append(key));
    return sha256(text.toString());
  }

  private static String sortedKey(Set<UUID> group) {
    return group.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
  }

  static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
