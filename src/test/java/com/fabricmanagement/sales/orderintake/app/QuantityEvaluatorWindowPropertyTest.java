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
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * STOCK-PROPOSAL-2 A3 (Fatih, 2026-10-13 to 2026-10-15): on small random stock, the cards are the
 * ones trying every subset finds. The rules are restated here on their own, without the evaluator's
 * classes:
 *
 * <ul>
 *   <li>A piece under 51% of its lot's average piece is short. A used lot is closed or keeps at
 *       least three normal pieces; a lot with fewer than three normal pieces is a remnant lot.
 *   <li>Ranking: more remnant lots closed, more lots closed, the deepest lot taken from partly
 *       shallower, the youngest lot used older, fewer lots; in a window then nearer the request,
 *       covering it on a tie. A list compared in order ties when it is a prefix of the other, so an
 *       added lot never counts on its own.
 *   <li>Several dye lots allowed: any lots combine. One required: each lot alone.
 *   <li>Cards (2026-10-15): the best meeting the request within the tolerance; the most within the
 *       upper limit when it is more; the best short within the lower limit. Only when none of these
 *       exists: the nearest above the upper limit, then the nearest below the lower limit. The
 *       first card is the opening.
 *   <li>Within a lot, as few normal pieces as the quantity allows: short pieces go first.
 * </ul>
 *
 * <p>Compared are the quantity and the ranking facts; among subsets that agree on them the
 * evaluator's choice is deterministic but need not be the subset met first here. Free stock only,
 * every piece eligible; data is fictional.
 */
class QuantityEvaluatorWindowPropertyTest {

  private static final Instant[] DATES = {
    Instant.parse("2025-01-01T00:00:00Z"),
    Instant.parse("2025-06-01T00:00:00Z"),
    Instant.parse("2026-01-01T00:00:00Z"),
    null
  };
  private static final String[] TOLERANCES = {"0", "2", "5", "10", "20"};
  private static final int MIN_NORMAL = 3;

  /** A subset's quantity and what the ranking compares. */
  private record Candidate(
      long quantity,
      int remnantClosed,
      int closed,
      List<Integer> partialSizes,
      List<Integer> youngestFirst,
      int lots) {}

  @Test
  void theCardsAreWhereTryingEverySubsetSaysTheyShouldBe() {
    Random random = new Random(20261015L);
    int covering = 0;
    int withShort = 0;
    for (int round = 0; round < 600; round++) {
      Case example = randomCase(random, round);
      QuantityEvaluationResult result = QuantityEvaluator.evaluate(example.input()).result();
      List<Candidate> all = everySubset(example);
      if (example.lots().stream().anyMatch(lot -> normalPieces(lot) < lot.pieces().size())) {
        withShort++;
      }

      assertThat(result.searchComplete()).as(example.toString()).isTrue();
      List<Candidate> expectedCards = expectedCards(all, example);
      if (expectedCards.isEmpty()) {
        assertThat(result.mainOptionIndex()).as(example.toString()).isNull();
        continue;
      }
      Candidate expected = expectedCards.getFirst();
      if (expected.quantity() >= example.target() && within(expected.quantity(), example)) {
        covering++;
      }
      assertThat(result.mainOptionIndex()).as(example.toString()).isZero();
      QuantityOption opening = result.options().get(result.mainOptionIndex());
      assertThat(candidateOf(opening, example))
          .as("%s: opening %s", example, opening.quantity())
          .isEqualTo(expected);
      assertThat(result.options().stream().map(option -> candidateOf(option, example)).toList())
          .as("%s: cards", example)
          .isEqualTo(expectedCards);
      assertThat(result.options())
          .as(example.toString())
          .allSatisfy(option -> assertThat(option.leavesRemnant()).isFalse());
      result.options().forEach(option -> assertShortPiecesWentFirst(option, example));
    }
    // The random cases must exercise the window and the short-piece rule, not only their absence.
    assertThat(covering).isGreaterThan(50);
    assertThat(withShort).isGreaterThan(50);
  }

  // ── The expected opening ─────────────────────────────────────────────────

  private static List<Candidate> expectedCards(List<Candidate> all, Case example) {
    long target = example.target();
    Comparator<Candidate> ranking =
        Comparator.comparingInt(Candidate::remnantClosed)
            .reversed()
            .thenComparing(Comparator.comparingInt(Candidate::closed).reversed())
            .thenComparing(Candidate::partialSizes, QuantityEvaluatorWindowPropertyTest::prefix)
            .thenComparing(Candidate::youngestFirst, QuantityEvaluatorWindowPropertyTest::prefix)
            .thenComparingInt(Candidate::lots);
    Comparator<Candidate> windowOrder =
        ranking
            .thenComparingLong(candidate -> Math.abs(candidate.quantity() - target))
            .thenComparing(candidate -> candidate.quantity() < target);
    Comparator<Candidate> mostFirst =
        Comparator.comparingLong(Candidate::quantity).reversed().thenComparing(ranking);
    List<Candidate> cards = new ArrayList<>();
    Candidate meets =
        all.stream()
            .filter(c -> c.quantity() >= target && within(c.quantity(), example))
            .min(windowOrder)
            .orElse(null);
    if (meets != null) {
      cards.add(meets);
      all.stream()
          .filter(c -> c.quantity() >= target && within(c.quantity(), example))
          .min(mostFirst)
          .filter(most -> most.quantity() > meets.quantity())
          .ifPresent(cards::add);
    }
    all.stream()
        .filter(c -> c.quantity() < target && within(c.quantity(), example))
        .min(windowOrder)
        .ifPresent(cards::add);
    if (!cards.isEmpty()) {
      return cards;
    }
    all.stream()
        .filter(c -> c.quantity() > target)
        .min(Comparator.comparingLong(Candidate::quantity).thenComparing(ranking))
        .ifPresent(cards::add);
    all.stream().filter(c -> c.quantity() < target).min(mostFirst).ifPresent(cards::add);
    return cards;
  }

  /** In order, lower first; a list that is a prefix of the other ties. */
  private static int prefix(List<Integer> left, List<Integer> right) {
    for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
      int compared = Integer.compare(left.get(i), right.get(i));
      if (compared != 0) {
        return compared;
      }
    }
    return 0;
  }

  /**
   * Every allowed subset of every compatible set of lots: a used lot is closed or keeps at least
   * three normal pieces.
   */
  private static List<Candidate> everySubset(Case example) {
    List<List<ProposalLot>> runs = new ArrayList<>();
    if (example.severalLots()) {
      runs.add(example.lots());
    } else {
      example.lots().forEach(lot -> runs.add(List.of(lot)));
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
            sum += metres(pieces.get(i));
          }
        }
        int remnantClosed = 0;
        int closed = 0;
        List<Integer> partialSizes = new ArrayList<>();
        List<Integer> youngestFirst = new ArrayList<>();
        for (ProposalLot lot : run) {
          int taken = 0;
          int normalLeft = 0;
          for (int i = 0; i < pieces.size(); i++) {
            if (owners.get(i) != lot) {
              continue;
            }
            if ((mask & (1 << i)) != 0) {
              taken++;
            } else if (!isShort(lot, pieces.get(i))) {
              normalLeft++;
            }
          }
          if (taken == 0) {
            continue;
          }
          boolean lotClosed = taken == lot.pieces().size();
          if (!lotClosed && normalLeft < MIN_NORMAL) {
            continue subsets;
          }
          if (lotClosed) {
            closed++;
            if (normalPieces(lot) < MIN_NORMAL) {
              remnantClosed++;
            }
          } else {
            partialSizes.add(lot.pieces().size());
          }
          youngestFirst.add(example.ageRank(lot));
        }
        partialSizes.sort(Comparator.reverseOrder());
        youngestFirst.sort(Comparator.reverseOrder());
        all.add(
            new Candidate(
                sum, remnantClosed, closed, partialSizes, youngestFirst, youngestFirst.size()));
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

  private static Candidate candidateOf(QuantityOption option, Case example) {
    int remnantClosed = 0;
    int closed = 0;
    List<Integer> partialSizes = new ArrayList<>();
    List<Integer> youngestFirst = new ArrayList<>();
    for (QuantityOption.LotPart part : option.lots()) {
      ProposalLot lot = example.lot(part.batchId());
      if (part.remainingEligiblePieces() == 0) {
        closed++;
        if (normalPieces(lot) < MIN_NORMAL) {
          remnantClosed++;
        }
      } else {
        partialSizes.add(lot.pieces().size());
      }
      youngestFirst.add(example.ageRank(lot));
    }
    partialSizes.sort(Comparator.reverseOrder());
    youngestFirst.sort(Comparator.reverseOrder());
    return new Candidate(
        option.quantity().longValueExact(),
        remnantClosed,
        closed,
        partialSizes,
        youngestFirst,
        option.lotCount());
  }

  /**
   * In each lot the option takes from partly, no other choice of pieces reaching the same quantity
   * and keeping three normal pieces uses fewer normal pieces: short pieces went first.
   */
  private static void assertShortPiecesWentFirst(QuantityOption opening, Case example) {
    for (QuantityOption.LotPart part : opening.lots()) {
      if (part.remainingEligiblePieces() == 0) {
        continue;
      }
      ProposalLot lot = example.lot(part.batchId());
      Set<UUID> taken = new HashSet<>(part.pieceIds());
      long quantity = part.quantity().longValueExact();
      int normalTaken =
          (int)
              lot.pieces().stream()
                  .filter(piece -> taken.contains(piece.stockUnitId()))
                  .filter(piece -> !isShort(lot, piece))
                  .count();
      int fewest = Integer.MAX_VALUE;
      List<ProposalPiece> pieces = lot.pieces();
      for (int mask = 1; mask < (1 << pieces.size()); mask++) {
        long sum = 0;
        int normal = 0;
        int normalLeft = 0;
        for (int i = 0; i < pieces.size(); i++) {
          boolean in = (mask & (1 << i)) != 0;
          boolean shortPiece = isShort(lot, pieces.get(i));
          if (in) {
            sum += metres(pieces.get(i));
            normal += shortPiece ? 0 : 1;
          } else if (!shortPiece) {
            normalLeft++;
          }
        }
        if (sum == quantity && normalLeft >= MIN_NORMAL) {
          fewest = Math.min(fewest, normal);
        }
      }
      assertThat(normalTaken)
          .as("%s: lot %s takes %s m with the fewest normal pieces", example, lot.lotNo(), quantity)
          .isEqualTo(fewest);
    }
  }

  // ── Short and normal pieces, restated ────────────────────────────────────

  private static long metres(ProposalPiece piece) {
    return piece.canonicalMeasure().longValueExact();
  }

  /** Under 51% of the lot's average piece: piece × 100 × count < total × 51. */
  private static boolean isShort(ProposalLot lot, ProposalPiece piece) {
    long total = lot.pieces().stream().mapToLong(QuantityEvaluatorWindowPropertyTest::metres).sum();
    return metres(piece) * 100 * lot.pieces().size() < total * 51;
  }

  private static int normalPieces(ProposalLot lot) {
    return (int) lot.pieces().stream().filter(piece -> !isShort(lot, piece)).count();
  }

  // ── Random stock ─────────────────────────────────────────────────────────

  private record Case(
      long target, String up, String down, List<ProposalLot> lots, boolean severalLots) {

    QuantityEvaluator.Input input() {
      BigDecimal requested = BigDecimal.valueOf(target);
      return new QuantityEvaluator.Input(
          requested,
          "M",
          "M",
          requested,
          Function.identity(),
          lots,
          List.of(),
          List.of(),
          !severalLots,
          new BigDecimal(up),
          new BigDecimal(down),
          QuantityEvaluator.StockHolds.NONE,
          List.of());
    }

    ProposalLot lot(UUID batchId) {
      return lots.stream().filter(lot -> lot.batchId().equals(batchId)).findFirst().orElseThrow();
    }

    /** Known dates oldest first share a rank when equal; an unknown date ranks last. */
    int ageRank(ProposalLot lot) {
      if (lot.productionDate() == null) {
        return Integer.MAX_VALUE;
      }
      List<Instant> dates =
          lots.stream()
              .map(ProposalLot::productionDate)
              .filter(Objects::nonNull)
              .distinct()
              .sorted()
              .toList();
      return dates.indexOf(lot.productionDate());
    }

    @Override
    public String toString() {
      StringBuilder text = new StringBuilder("target " + target + " +" + up + "/-" + down);
      text.append(severalLots ? " several lots" : " single lot");
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
        // Few distinct lengths, so many subsets share a total and the ranking decides; 25 m next
        // to 100 m pieces is short.
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
