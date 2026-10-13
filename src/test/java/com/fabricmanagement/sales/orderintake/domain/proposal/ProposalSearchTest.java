package com.fabricmanagement.sales.orderintake.domain.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalSearch.Result;
import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalSearch.SearchLot;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch.Lot;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch.LotTake;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch.Piece;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch.Selection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The proposal searches (STOCK-PROPOSAL-2; Fatih, 2026-10-12 and 2026-10-13): a lot is closed or
 * keeps at least three pieces; more lots closed, older stock and fewer lots come first. Units are
 * centimetres: 100 m = 10_000. Lots are passed oldest first. Data is fictional.
 */
class ProposalSearchTest {

  private static final long M = 100;

  // ── Never one or two pieces left ─────────────────────────────────────────

  @Test
  void pieces120_110_90For200_noTakeLeavesOneOrTwoPieces_soOnlyTheWholeLotIsOffered() {
    Lot only = lot(120, 110, 90);

    Result within = ProposalSearch.bestWithin(List.of(window(only, 0)), 190 * M, 210 * M, 200 * M);
    Result above = ProposalSearch.smallestFrom(List.of(window(only, 0)), 200 * M);
    Result below = ProposalSearch.largestUpTo(List.of(window(only, 0)), 199 * M);

    assertThat(within.best()).isEmpty();
    assertThat(within.limitExceeded()).isFalse();
    assertThat(above.best().orElseThrow().total()).isEqualTo(320 * M);
    assertThat(above.best().orElseThrow().takes().getFirst().remainingPieces()).isZero();
    assertThat(below.best()).isEmpty();
  }

  @Test
  void aLotOfFourMayKeepThree_neverTwo() {
    Lot four = lot(100, 100, 100, 100);

    Selection one =
        ProposalSearch.bestWithin(List.of(window(four, 0)), 100 * M, 100 * M, 100 * M)
            .best()
            .orElseThrow();
    Result two = ProposalSearch.bestWithin(List.of(window(four, 0)), 200 * M, 200 * M, 200 * M);

    assertThat(one.takes().getFirst().remainingPieces()).isEqualTo(3);
    assertThat(two.best()).isEmpty();
  }

  @Test
  void s03_500mRequested_theNearestAboveClosesTheLot_theNearestBelowKeepsThree() {
    Lot p3 = lot(102, 103, 101, 104, 104);

    Selection above =
        ProposalSearch.smallestFrom(List.of(window(p3, 0)), 500 * M).best().orElseThrow();
    Selection below =
        ProposalSearch.largestUpTo(List.of(window(p3, 0)), 499 * M).best().orElseThrow();

    assertThat(above.total()).isEqualTo(514 * M);
    assertThat(below.total()).isEqualTo(208 * M);
    assertThat(below.takes().getFirst().remainingPieces()).isEqualTo(3);
    assertSelectionAddsUp(above, p3);
    assertSelectionAddsUp(below, p3);
  }

  // ── The ranking within a window ──────────────────────────────────────────

  @Test
  void theClosingReviewsCounterexample_closesBAndC() {
    Lot a = lot(100, 100);
    Lot b = lot(50, 50, 50);
    Lot c = lot(75, 75);

    Selection best =
        ProposalSearch.bestWithin(
                List.of(window(a, 0), window(b, 1), window(c, 2)), 300 * M, 315 * M, 300 * M)
            .best()
            .orElseThrow();

    assertThat(best.total()).isEqualTo(300 * M);
    assertThat(best.takes()).extracting(LotTake::lotId).containsExactly(b.id(), c.id());
    assertThat(best.takes()).allSatisfy(take -> assertThat(take.remainingPieces()).isZero());
  }

  @Test
  void moreClosedLotsComeBeforeOlderStock_andOlderStockBeforeFewerLots() {
    Lot oldest = lot(100, 100, 100);
    Lot older = lot(150);
    Lot newer = lot(150);
    List<SearchLot> lots = List.of(window(oldest, 0), window(older, 1), window(newer, 2));

    assertThat(ProposalSearch.bestWithin(lots, 300 * M, 300 * M, 300 * M).best().orElseThrow())
        .extracting(selection -> selection.takes().stream().map(LotTake::lotId).toList())
        .isEqualTo(List.of(older.id(), newer.id()));
    assertThat(ProposalSearch.bestWithin(lots, 150 * M, 150 * M, 150 * M).best().orElseThrow())
        .extracting(selection -> selection.takes().stream().map(LotTake::lotId).toList())
        .isEqualTo(List.of(older.id()));
  }

  @Test
  void belowTheRequestTheBestClosesTheOldLot_andCompletesFromTheNewOne() {
    // 700 m with 5%: the window below the request is 665–699 m.
    Lot old = lot(95, 95, 100);
    Lot fresh = lot(100, 100, 100, 100, 100, 100, 100, 100, 100, 100);

    Selection best =
        ProposalSearch.bestWithin(
                List.of(window(old, 0), window(fresh, 1)), 665 * M, 699 * M, 700 * M)
            .best()
            .orElseThrow();

    assertThat(best.total()).isEqualTo(690 * M);
    assertThat(best.takes().getFirst().lotId()).isEqualTo(old.id());
    assertThat(best.takes().getFirst().remainingPieces()).isZero();
    assertThat(best.takes().get(1).remainingPieces()).isEqualTo(6);
  }

  // ── Nearest first, then the ranking ──────────────────────────────────────

  @Test
  void theNearestAboveWinsOverOlderStock_theRankingDecidesOnlyBetweenEqualTotals() {
    Lot older = lot(250, 250, 250);
    Lot newer = lot(180, 180, 180, 180);
    List<SearchLot> lots = List.of(window(older, 0), window(newer, 1));

    Selection above = ProposalSearch.smallestFrom(lots, 700 * M).best().orElseThrow();
    assertThat(above.total()).isEqualTo(720 * M);
    assertThat(above.takes()).extracting(LotTake::lotId).containsExactly(newer.id());

    Lot first = lot(100);
    Lot second = lot(100);
    Selection below =
        ProposalSearch.largestUpTo(List.of(window(first, 0), window(second, 1)), 150 * M)
            .best()
            .orElseThrow();
    assertThat(below.takes()).extracting(LotTake::lotId).containsExactly(first.id());
  }

  // ── Capacity and limits ──────────────────────────────────────────────────

  @Test
  void aLotsCapacityBoundsEveryTake_freeStockNetOfQuoteIntents() {
    Lot capped = new Lot(UUID.randomUUID(), pieces(100, 100, 100, 100), 150 * M);

    assertThat(
            ProposalSearch.smallestFrom(List.of(window(capped, 0)), 100 * M)
                .best()
                .orElseThrow()
                .total())
        .isEqualTo(100 * M);
    assertThat(
            ProposalSearch.bestWithin(List.of(window(capped, 0)), 180 * M, 220 * M, 200 * M).best())
        .isEmpty();
  }

  @Test
  void aLotWithNoCapacityOrNoPiecesOffersNothing_withoutALimit() {
    Lot none = new Lot(UUID.randomUUID(), pieces(100, 100, 100), 0);
    Lot empty = new Lot(UUID.randomUUID(), List.of());

    Result result =
        ProposalSearch.smallestFrom(List.of(window(none, 0), window(empty, 1)), 100 * M);

    assertThat(result.best()).isEmpty();
    assertThat(result.limitExceeded()).isFalse();
  }

  @Test
  void beyondTheLotLimitTheSearchRefusesInsteadOfGuessing() {
    List<SearchLot> lots = new ArrayList<>();
    for (int i = 0; i <= WholePieceSearch.MAX_LOTS; i++) {
      lots.add(window(lot(100), i));
    }

    Result result = ProposalSearch.smallestFrom(lots, 100 * M);

    assertThat(result.limitExceeded()).isTrue();
    assertThat(result.best()).isEmpty();
  }

  private static void assertSelectionAddsUp(Selection selection, Lot lot) {
    Set<UUID> chosen =
        selection.takes().stream()
            .flatMap(take -> take.pieceIds().stream())
            .collect(Collectors.toSet());
    long sum =
        lot.pieces().stream()
            .filter(piece -> chosen.contains(piece.id()))
            .mapToLong(Piece::units)
            .sum();
    assertThat(sum).isEqualTo(selection.total());
  }

  private static SearchLot window(Lot lot, int ageRank) {
    return new SearchLot(lot, ageRank);
  }

  private static Lot lot(long... metres) {
    return new Lot(UUID.randomUUID(), pieces(metres));
  }

  private static List<Piece> pieces(long... metres) {
    List<Piece> pieces = new ArrayList<>();
    for (long length : metres) {
      pieces.add(new Piece(UUID.randomUUID(), length * M));
    }
    return pieces;
  }
}
