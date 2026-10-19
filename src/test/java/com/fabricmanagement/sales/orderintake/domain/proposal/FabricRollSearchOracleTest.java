package com.fabricmanagement.sales.orderintake.domain.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Independent exhaustive piece subsets, not the production pattern/bitset algorithm. */
class FabricRollSearchOracleTest {
  record Candidate(
      long total, int closedRemnants, List<Integer> partial, List<Integer> ages, int lots) {}

  record AnchorChoice(Candidate candidate, int physical, int freeOnly) {}

  @Test
  void smallRandomStocksMatchExhaustiveBusinessRulesIncludingShortPiecesAndCapacity() {
    Random random = new Random(24011);
    for (int sample = 0; sample < 60; sample++) {
      List<ProposalSearch.SearchLot> lots = new ArrayList<>();
      for (int i = 0; i < 2 + sample % 2; i++) {
        List<WholePieceSearch.Piece> pieces = new ArrayList<>();
        for (int j = 0, count = 1 + random.nextInt(5); j < count; j++) {
          pieces.add(
              new WholePieceSearch.Piece(
                  new UUID(i + 1, j + 1), 1 + random.nextInt(12), random.nextInt(4) == 0));
        }
        long total = pieces.stream().mapToLong(WholePieceSearch.Piece::units).sum();
        long capacity = sample % 3 == 0 ? random.nextInt((int) total + 1) : total;
        lots.add(
            new ProposalSearch.SearchLot(
                new WholePieceSearch.Lot(new UUID(0, i + 1), pieces, capacity),
                i == 2 ? Integer.MAX_VALUE : i,
                4,
                random.nextBoolean()));
      }
      long target = 1 + random.nextInt(50);
      check(lots, target);
      // Single-lot mode searches each lot independently with the same policy.
      for (var lot : lots) check(List.of(lot), target);
    }
  }

  private static void check(List<ProposalSearch.SearchLot> lots, long target) {
    List<Candidate> candidates = enumerate(lots);
    AnchorChoice completion = null;
    for (int anchor = 0; anchor < (1 << lots.size()); anchor++) {
      long amount = 0;
      boolean valid = true;
      for (int i = 0; i < lots.size(); i++) {
        if ((anchor & (1 << i)) == 0) continue;
        var lot = lots.get(i);
        long normals = lot.lot().pieces().stream().filter(piece -> !piece.shortPiece()).count();
        if (normals >= 4) valid = false;
        amount += lot.lot().pieces().stream().mapToLong(WholePieceSearch.Piece::units).sum();
      }
      if (!valid || amount > target) continue;
      final int required = anchor;
      Candidate smallest =
          candidates.stream()
              .filter(
                  candidate ->
                      candidate.total >= target
                          && (candidate.closedRemnants & required) == required)
              .min(
                  Comparator.comparingLong(Candidate::total)
                      .thenComparing(
                          (left, right) -> compare(score(left, lots), score(right, lots))))
              .orElse(null);
      if (smallest == null) continue;
      var choice = score(smallest, lots);
      if (completion == null || compare(choice, completion) < 0) completion = choice;
    }
    var found = ProposalSearch.fabricCompletion(lots, target);
    assertThat(found.limitExceeded()).isFalse();
    assertThat(found.best().isPresent()).isEqualTo(completion != null);
    if (completion != null) {
      assertThat(found.best().orElseThrow().total()).isEqualTo(completion.candidate.total);
      assertThat(found.fabricRank().physicalRemnants()).isEqualTo(completion.physical);
      assertThat(found.fabricRank().freeOnlyRemnants()).isEqualTo(completion.freeOnly);
      assertThat(found.fabricRank().partialNormals()).isEqualTo(completion.candidate.partial);
      assertThat(found.fabricRank().youngestFirst()).isEqualTo(completion.candidate.ages);
    }
    Long below =
        candidates.stream()
            .filter(candidate -> candidate.total < target)
            .map(Candidate::total)
            .max(Long::compare)
            .orElse(null);
    var foundBelow = ProposalSearch.fabricBelow(lots, target);
    assertThat(foundBelow.limitExceeded()).isFalse();
    assertThat(foundBelow.best().map(WholePieceSearch.Selection::total).orElse(null))
        .isEqualTo(below);
  }

  private static List<Candidate> enumerate(List<ProposalSearch.SearchLot> lots) {
    int count = lots.stream().mapToInt(lot -> lot.lot().pieces().size()).sum();
    List<Candidate> result = new ArrayList<>();
    for (int subset = 1; subset < (1 << count); subset++) {
      int offset = 0, closed = 0, used = 0;
      long total = 0;
      boolean legal = true;
      List<Integer> partial = new ArrayList<>();
      List<Integer> ages = new ArrayList<>();
      for (int i = 0; i < lots.size(); i++) {
        var source = lots.get(i);
        int selected = 0, normalLeft = 0, normals = 0;
        long taken = 0;
        for (var piece : source.lot().pieces()) {
          if (!piece.shortPiece()) normals++;
          if ((subset & (1 << offset)) != 0) {
            selected++;
            taken += piece.units();
          } else if (!piece.shortPiece()) normalLeft++;
          offset++;
        }
        if (selected == 0) continue;
        if (taken > source.lot().capacity()) legal = false;
        boolean all = selected == source.lot().pieces().size();
        if (!all && normalLeft < 4) legal = false;
        if (all && normals < 4) closed |= 1 << i;
        if (!all) partial.add(normals);
        ages.add(source.ageRank());
        total += taken;
        used++;
      }
      if (legal) {
        partial.sort(Comparator.reverseOrder());
        ages.sort(Comparator.reverseOrder());
        result.add(new Candidate(total, closed, partial, ages, used));
      }
    }
    return result;
  }

  private static AnchorChoice score(Candidate candidate, List<ProposalSearch.SearchLot> lots) {
    int physical = 0, freeOnly = 0;
    for (int i = 0; i < lots.size(); i++) {
      if ((candidate.closedRemnants & (1 << i)) != 0) {
        if (lots.get(i).physicalClosure()) physical++;
        else freeOnly++;
      }
    }
    return new AnchorChoice(candidate, physical, freeOnly);
  }

  private static int compare(AnchorChoice left, AnchorChoice right) {
    if (left.physical != right.physical) return Integer.compare(right.physical, left.physical);
    if (left.freeOnly != right.freeOnly) return Integer.compare(right.freeOnly, left.freeOnly);
    if (left.candidate.total != right.candidate.total)
      return Long.compare(left.candidate.total, right.candidate.total);
    return tie(left.candidate, right.candidate);
  }

  private static int tie(Candidate left, Candidate right) {
    int value = vector(left.partial, right.partial);
    if (value == 0) value = vector(left.ages, right.ages);
    return value != 0 ? value : Integer.compare(left.lots, right.lots);
  }

  private static int vector(List<Integer> left, List<Integer> right) {
    for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
      if (i == left.size()) return -1;
      if (i == right.size()) return 1;
      if (!left.get(i).equals(right.get(i))) return Integer.compare(left.get(i), right.get(i));
    }
    return 0;
  }

  @Test
  void boundsAndExhaustedBudgetsReportIncompleteSearch() {
    List<ProposalSearch.SearchLot> twelve = new ArrayList<>();
    for (int i = 1; i <= 12; i++)
      twelve.add(
          new ProposalSearch.SearchLot(
              new WholePieceSearch.Lot(
                  new UUID(0, i), List.of(new WholePieceSearch.Piece(new UUID(i, 1), 100))),
              0,
              4,
              true));
    assertThat(ProposalSearch.fabricCompletion(twelve, 600).limitExceeded()).isFalse();
    List<WholePieceSearch.Piece> sixty =
        java.util.stream.IntStream.rangeClosed(1, 60)
            .mapToObj(i -> new WholePieceSearch.Piece(new UUID(1, i), 100))
            .toList();
    var lot =
        new ProposalSearch.SearchLot(new WholePieceSearch.Lot(new UUID(0, 1), sixty), 0, 4, true);
    assertThat(ProposalSearch.fabricCompletion(List.of(lot), 100).limitExceeded()).isFalse();
    assertThat(
            ProposalSearch.fabricCompletion(List.of(lot), 100, 0, ProposalSearch.SUM_BUDGET)
                .limitExceeded())
        .isTrue();
    assertThat(
            ProposalSearch.fabricCompletion(List.of(lot), 100, ProposalSearch.PATTERN_BUDGET, 0)
                .limitExceeded())
        .isTrue();
    var wide =
        new ProposalSearch.SearchLot(
            new WholePieceSearch.Lot(
                new UUID(0, 2),
                List.of(
                    new WholePieceSearch.Piece(new UUID(2, 1), 1_000_001),
                    new WholePieceSearch.Piece(new UUID(2, 2), 1_000_002))),
            0,
            4,
            true);
    assertThat(ProposalSearch.fabricCompletion(List.of(wide), 100).limitExceeded()).isTrue();
  }

  @Test
  void comparatorHasNoPrefixCyclesAndIsAntisymmetricAndTransitive() {
    List<FabricRollRank> ranks = new ArrayList<>();
    int id = 0;
    for (List<Integer> partial :
        List.of(List.<Integer>of(), List.of(4), List.of(4, 3), List.of(5))) {
      for (List<Integer> age :
          List.of(List.<Integer>of(), List.of(0), List.of(0, 0), List.of(Integer.MAX_VALUE))) {
        ranks.add(new FabricRollRank(1, 0, 700, partial, age, ++id % 3, "R" + id));
      }
    }
    for (var order : List.of(FabricRollRank.COMPLETION, FabricRollRank.BELOW)) {
      for (var a : ranks)
        for (var b : ranks) {
          assertThat(Integer.signum(order.compare(a, b)))
              .isEqualTo(-Integer.signum(order.compare(b, a)));
          for (var c : ranks)
            if (order.compare(a, b) <= 0 && order.compare(b, c) <= 0) {
              assertThat(order.compare(a, c)).isLessThanOrEqualTo(0);
            }
        }
    }
  }
}
