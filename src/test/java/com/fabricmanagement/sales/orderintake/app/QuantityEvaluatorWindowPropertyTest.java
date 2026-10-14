package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.WidthEvidence;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * STOCK-PROPOSAL-2 A (Fatih, 2026-10-13): on small random stock, the option the proposal opens at
 * is the one trying every subset finds. No subset may leave one or two pieces in a lot it uses. The
 * opening is the best meeting the request within the tolerance (more lots closed, older stock,
 * fewer lots, closeness), else the nearest above, else the best below within the tolerance, else
 * the nearest below. Compared are the quantity and the ranking facts; among subsets that agree on
 * them the evaluator's choice is deterministic but need not be the subset met first here. Free
 * stock only, every piece eligible; data is fictional.
 */
class QuantityEvaluatorWindowPropertyTest {

  private static final Instant[] DATES = {
    Instant.parse("2025-01-01T00:00:00Z"),
    Instant.parse("2025-06-01T00:00:00Z"),
    Instant.parse("2026-01-01T00:00:00Z"),
    null
  };
  private static final String[] TOLERANCES = {"0", "2", "5", "10", "20"};

  /** A subset's quantity and what the ranking compares. */
  private record Candidate(long quantity, int closed, List<Instant> dates, int lots) {}

  @Test
  void theProposalOpensWhereTryingEverySubsetSaysItShould() {
    Random random = new Random(20261013L);
    int covering = 0;
    for (int round = 0; round < 600; round++) {
      Case example = randomCase(random, round);
      QuantityEvaluationResult result = QuantityEvaluator.evaluate(example.input()).result();
      List<Candidate> all = everySubset(example);
      Candidate expected = expectedOpening(all, example);

      assertThat(result.searchComplete()).as(example.toString()).isTrue();
      if (expected == null) {
        assertThat(result.mainOptionIndex()).as(example.toString()).isNull();
        continue;
      }
      if (expected.quantity() >= example.target() && within(expected.quantity(), example)) {
        covering++;
      }
      assertThat(result.mainOptionIndex()).as(example.toString()).isNotNull();
      QuantityOption opening = result.options().get(result.mainOptionIndex());
      assertThat(candidateOf(opening))
          .as("%s: opening %s", example, opening.quantity())
          .isEqualTo(expected);
      assertThat(result.options())
          .as(example.toString())
          .allSatisfy(option -> assertThat(option.leavesRemnant()).isFalse());
      // One card of each kind beside the opening (Fatih, 2026-10-14): one short, and one above
      // only outside the tolerance.
      List<QuantityOption> besides =
          result.options().stream()
              .filter(option -> option.placement() != QuantityOption.OptionPlacement.MAIN)
              .filter(option -> option.source() == QuantityOption.StockSource.FREE)
              .filter(
                  option ->
                      option.compatibility() != QuantityOption.Compatibility.PENDING_CONFIRMATION)
              .toList();
      assertThat(besides)
          .as(example.toString())
          .filteredOn(option -> option.difference().signum() < 0)
          .hasSizeLessThanOrEqualTo(1);
      assertThat(besides)
          .as(example.toString())
          .filteredOn(option -> option.difference().signum() >= 0)
          .hasSizeLessThanOrEqualTo(1)
          .allSatisfy(
              option ->
                  assertThat(option.toleranceStatus())
                      .isEqualTo(QuantityOption.ToleranceStatus.OUTSIDE));
    }
    // The random cases must exercise the window, not only its absence.
    assertThat(covering).isGreaterThan(50);
  }

  // ── The expected opening ─────────────────────────────────────────────────

  private static Candidate expectedOpening(List<Candidate> all, Case example) {
    long target = example.target();
    Comparator<Candidate> ranking =
        Comparator.comparingInt(Candidate::closed)
            .reversed()
            .thenComparing(Candidate::dates, ProposalRanking::compareAges)
            .thenComparingInt(Candidate::lots);
    Comparator<Candidate> windowOrder =
        ranking
            .thenComparingLong(candidate -> Math.abs(candidate.quantity() - target))
            .thenComparing(candidate -> candidate.quantity() < target);
    Candidate coveringBest =
        all.stream()
            .filter(c -> c.quantity() >= target && within(c.quantity(), example))
            .min(windowOrder)
            .orElse(null);
    if (coveringBest != null) {
      return coveringBest;
    }
    Candidate above =
        all.stream()
            .filter(c -> c.quantity() >= target)
            .min(Comparator.comparingLong(Candidate::quantity).thenComparing(ranking))
            .orElse(null);
    if (above != null) {
      return above;
    }
    Candidate shortWithin =
        all.stream()
            .filter(c -> c.quantity() < target && within(c.quantity(), example))
            .min(windowOrder)
            .orElse(null);
    if (shortWithin != null) {
      return shortWithin;
    }
    return all.stream()
        .filter(c -> c.quantity() < target)
        .min(Comparator.comparingLong(Candidate::quantity).reversed().thenComparing(ranking))
        .orElse(null);
  }

  /** Every allowed subset of every compatible set of lots: no used lot keeps one or two pieces. */
  private static List<Candidate> everySubset(Case example) {
    List<List<ProposalLot>> runs = new ArrayList<>();
    example.lots().forEach(lot -> runs.add(List.of(lot)));
    if (example.grouped() && example.lots().size() > 1) {
      runs.add(example.lots());
    }
    List<Candidate> all = new ArrayList<>();
    for (List<ProposalLot> run : runs) {
      List<ProposalPiece> pieces = new ArrayList<>();
      List<ProposalLot> owners = new ArrayList<>();
      for (ProposalLot lot : run) {
        for (ProposalPiece piece : lot.pieces()) {
          pieces.add(piece);
          owners.add(lot);
        }
      }
      subsets:
      for (int mask = 1; mask < (1 << pieces.size()); mask++) {
        long sum = 0;
        for (int i = 0; i < pieces.size(); i++) {
          if ((mask & (1 << i)) != 0) {
            sum += pieces.get(i).canonicalMeasure().longValueExact();
          }
        }
        int closed = 0;
        List<Instant> dates = new ArrayList<>();
        for (ProposalLot lot : run) {
          int taken = 0;
          for (int i = 0; i < pieces.size(); i++) {
            if ((mask & (1 << i)) != 0 && owners.get(i) == lot) {
              taken++;
            }
          }
          if (taken == 0) {
            continue;
          }
          int left = lot.pieces().size() - taken;
          if (left == 1 || left == 2) {
            continue subsets;
          }
          if (left == 0) {
            closed++;
          }
          dates.add(lot.productionDate());
        }
        dates.sort(Comparator.nullsLast(Comparator.naturalOrder()));
        all.add(new Candidate(sum, closed, dates, dates.size()));
      }
    }
    return all;
  }

  /** The evaluator's rule: outside when |difference| × 100 exceeds request × agreed percent. */
  private static boolean within(long quantity, Case example) {
    long difference = quantity - example.target();
    if (difference == 0) {
      return true;
    }
    BigDecimal limit = new BigDecimal(difference > 0 ? example.up() : example.down());
    return BigDecimal.valueOf(Math.abs(difference))
            .multiply(BigDecimal.valueOf(100))
            .compareTo(BigDecimal.valueOf(example.target()).multiply(limit))
        <= 0;
  }

  private static Candidate candidateOf(QuantityOption option) {
    int closed =
        (int) option.lots().stream().filter(part -> part.remainingEligiblePieces() == 0).count();
    List<Instant> dates = new ArrayList<>();
    option.lots().forEach(part -> dates.add(part.productionDate()));
    dates.sort(Comparator.nullsLast(Comparator.naturalOrder()));
    return new Candidate(option.quantity().longValueExact(), closed, dates, option.lotCount());
  }

  // ── Random stock ─────────────────────────────────────────────────────────

  private record Case(
      long target, String up, String down, List<ProposalLot> lots, boolean grouped) {

    QuantityEvaluator.Input input() {
      List<Set<UUID>> groups = new ArrayList<>();
      if (grouped && lots.size() > 1) {
        Set<UUID> all = new HashSet<>();
        lots.forEach(lot -> all.add(lot.batchId()));
        groups.add(all);
      }
      BigDecimal requested = BigDecimal.valueOf(target);
      return new QuantityEvaluator.Input(
          requested,
          "M",
          "M",
          requested,
          Function.identity(),
          lots,
          groups,
          List.of(),
          false,
          new BigDecimal(up),
          new BigDecimal(down),
          QuantityEvaluator.StockHolds.NONE,
          List.of());
    }

    @Override
    public String toString() {
      StringBuilder text = new StringBuilder("target " + target + " +" + up + "/-" + down);
      text.append(grouped ? " grouped" : " single lots");
      for (ProposalLot lot : lots) {
        text.append(" | ").append(lot.lotNo()).append(' ').append(lot.productionDate());
        lot.pieces().forEach(piece -> text.append(' ').append(piece.canonicalMeasure()));
      }
      return text.toString();
    }
  }

  private static Case randomCase(Random random, int round) {
    int lotCount = 1 + random.nextInt(3);
    int piecesLeft = 10;
    List<ProposalLot> lots = new ArrayList<>();
    long total = 0;
    for (int l = 0; l < lotCount && piecesLeft > 0; l++) {
      int pieces = 1 + random.nextInt(Math.min(6, piecesLeft));
      piecesLeft -= pieces;
      UUID lotId = new UUID(round + 1L, l);
      List<ProposalPiece> list = new ArrayList<>();
      for (int p = 0; p < pieces; p++) {
        // Few distinct lengths, so many subsets share a total and the ranking decides.
        long metres = 25L * (1 + random.nextInt(5));
        total += metres;
        list.add(
            new ProposalPiece(
                new UUID(lotId.getLeastSignificantBits() + 1_000L * (round + 1), p),
                "R" + p,
                BigDecimal.valueOf(metres),
                PieceState.ELIGIBLE,
                null,
                0L));
      }
      lots.add(
          new ProposalLot(
              lotId,
              "L" + l,
              DATES[random.nextInt(DATES.length)],
              PrimaryMeasure.LENGTH,
              "M",
              WidthEvidence.NOT_REQUIRED,
              list));
    }
    long target = Math.max(10, (long) (total * (0.2 + 0.7 * random.nextDouble())));
    return new Case(
        target,
        TOLERANCES[random.nextInt(TOLERANCES.length)],
        TOLERANCES[random.nextInt(TOLERANCES.length)],
        lots,
        random.nextBoolean());
  }
}
