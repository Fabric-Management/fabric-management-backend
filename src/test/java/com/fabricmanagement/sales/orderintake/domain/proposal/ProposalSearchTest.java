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
 * The proposal searches (STOCK-PROPOSAL-2; Fatih, 2026-10-12 to 2026-10-14): a lot is closed or
 * keeps at least its lot-health minimum of normal pieces, three by default; short pieces do not
 * count and go first. A remnant lot closed, more lots closed, the smaller lot completed from, older
 * stock by the youngest lot used and fewer lots come first. Units are centimetres: 100 m = 10_000.
 * Lots are passed oldest first; the caller marks short pieces. Data is fictional.
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

  @Test
  void aTenantMinimumOfFive_keepsFivePieces_orClosesTheLot() {
    // 2026-10-14: what a lot keeps is the tenant's lot-health minimum, three by default.
    Lot eight = lot(100, 100, 100, 100, 100, 100, 100, 100);
    SearchLot keepFive = new SearchLot(eight, 0, 5);

    Selection three =
        ProposalSearch.bestWithin(List.of(keepFive), 300 * M, 300 * M, 300 * M)
            .best()
            .orElseThrow();
    Result four = ProposalSearch.bestWithin(List.of(keepFive), 400 * M, 400 * M, 400 * M);
    Selection whole = ProposalSearch.smallestFrom(List.of(keepFive), 400 * M).best().orElseThrow();

    assertThat(three.takes().getFirst().remainingPieces()).isEqualTo(5);
    assertThat(four.best()).isEmpty();
    assertThat(whole.total()).isEqualTo(800 * M);
    assertThat(whole.takes().getFirst().remainingPieces()).isZero();
    // The standard minimum still lets four pieces go and keeps four.
    assertThat(
            ProposalSearch.bestWithin(List.of(window(eight, 0)), 400 * M, 400 * M, 400 * M).best())
        .isPresent();
  }

  // ── Short pieces (Fatih, 2026-10-14) ─────────────────────────────────────

  @Test
  void aShortPieceDoesNotCountAsKept_soTwoNormalPiecesAndAShortOneCannotStay() {
    // 100 + 100 + 100 + 100 + 40 m, the 40 m piece under 51% of the average. 200 m would leave
    // 100 + 100 + 40: two normal pieces, under the minimum. 140 m leaves three normal pieces.
    Lot lot = lot(normal(100), normal(100), normal(100), normal(100), shortPiece(40));

    Result twoHundred =
        ProposalSearch.bestWithin(List.of(window(lot, 0)), 200 * M, 200 * M, 200 * M);
    Selection above =
        ProposalSearch.smallestFrom(List.of(window(lot, 0)), 200 * M).best().orElseThrow();
    Selection oneForty =
        ProposalSearch.bestWithin(List.of(window(lot, 0)), 140 * M, 140 * M, 140 * M)
            .best()
            .orElseThrow();

    assertThat(twoHundred.best()).isEmpty();
    assertThat(above.total()).isEqualTo(440 * M);
    assertThat(above.takes().getFirst().remainingPieces()).isZero();
    assertThat(oneForty.takes().getFirst().remainingPieces()).isEqualTo(3);
    assertSelectionAddsUp(oneForty, lot);
  }

  @Test
  void shortPiecesGoIntoTheOrderFirst_whenTheQuantityAllows() {
    Lot lot =
        lot(normal(100), normal(100), normal(100), normal(100), shortPiece(60), shortPiece(40));
    Set<UUID> shortIds =
        lot.pieces().stream().filter(Piece::shortPiece).map(Piece::id).collect(Collectors.toSet());

    Selection hundred =
        ProposalSearch.bestWithin(List.of(window(lot, 0)), 100 * M, 100 * M, 100 * M)
            .best()
            .orElseThrow();
    Selection twoHundred =
        ProposalSearch.bestWithin(List.of(window(lot, 0)), 200 * M, 200 * M, 200 * M)
            .best()
            .orElseThrow();

    // 100 m is the two short pieces, not a normal one; 200 m is one normal piece and both short
    // ones, never two normal pieces.
    assertThat(hundred.takes().getFirst().pieceIds()).containsExactlyInAnyOrderElementsOf(shortIds);
    assertThat(twoHundred.takes().getFirst().pieceIds()).containsAll(shortIds).hasSize(3);
    assertThat(twoHundred.takes().getFirst().remainingPieces()).isEqualTo(3);
    assertSelectionAddsUp(twoHundred, lot);
  }

  @Test
  void aLotAlreadyUnderTheMinimum_isClosedOrLeftAlone() {
    // 40 + 30 + 55 m left of a lot whose pieces averaged 100 m: one normal piece, a remnant.
    Lot remnant = lot(shortPiece(40), shortPiece(30), normal(55));

    Result part = ProposalSearch.bestWithin(List.of(window(remnant, 0)), 40 * M, 70 * M, 55 * M);
    Selection whole =
        ProposalSearch.smallestFrom(List.of(window(remnant, 0)), 50 * M).best().orElseThrow();

    assertThat(part.best()).isEmpty();
    assertThat(whole.total()).isEqualTo(125 * M);
    assertThat(whole.takes().getFirst().remainingPieces()).isZero();
  }

  // ── The ranking within a window (Fatih, 2026-10-14) ──────────────────────

  @Test
  void fatihsExample_closingTheRemnantLotComesBeforeTheExactQuantity() {
    // 700 m with 5%: LOT-A 40 + 30 + 55 m (pieces averaged 100 m), LOT-B 20 × 100 m. LOT-B alone
    // gives 700 m exactly; closing LOT-A and six pieces of LOT-B gives 725 m, and opens.
    Lot lotA = lot(shortPiece(40), shortPiece(30), normal(55));
    long[] twenty = new long[20];
    java.util.Arrays.fill(twenty, 100);
    Lot lotB = lot(twenty);

    Selection best =
        ProposalSearch.bestWithin(
                List.of(window(lotB, 0), window(lotA, 1)), 700 * M, 735 * M, 700 * M)
            .best()
            .orElseThrow();

    assertThat(best.total()).isEqualTo(725 * M);
    assertThat(best.takes()).extracting(LotTake::lotId).containsExactly(lotB.id(), lotA.id());
    assertThat(best.takes().get(1).remainingPieces()).isZero();
    assertThat(best.takes().get(0).remainingPieces()).isEqualTo(14);
  }

  @Test
  void aRemnantLotClosed_comesBeforeAHealthyLotClosed_andBeforeOlderStock() {
    Lot healthy = lot(100, 100, 100);
    Lot remnant = lot(shortPiece(40), shortPiece(30), normal(55));
    Lot deep = lot(175, 175, 175, 175, 175);
    List<SearchLot> lots = List.of(window(healthy, 0), window(remnant, 1), window(deep, 2));

    Selection best =
        ProposalSearch.bestWithin(lots, 300 * M, 300 * M, 300 * M).best().orElseThrow();

    // 300 m closes the oldest, healthy lot in one piece of work; closing the remnant lot and
    // completing 175 m from the deep lot still comes first.
    assertThat(best.takes()).extracting(LotTake::lotId).containsExactly(remnant.id(), deep.id());
    assertThat(best.takes().getFirst().remainingPieces()).isZero();
    assertThat(best.takes().get(1).remainingPieces()).isEqualTo(4);
  }

  @Test
  void completingFromTheLotWithFewerPieces_comesBeforeOlderStock() {
    long[] twentyFour = new long[24];
    java.util.Arrays.fill(twentyFour, 100);
    Lot big = lot(twentyFour);
    Lot small = lot(100, 100, 100, 100, 100, 100);

    Selection best =
        ProposalSearch.bestWithin(
                List.of(window(big, 0), window(small, 1)), 300 * M, 300 * M, 300 * M)
            .best()
            .orElseThrow();

    assertThat(best.takes()).extracting(LotTake::lotId).containsExactly(small.id());
    assertThat(best.takes().getFirst().remainingPieces()).isEqualTo(3);
  }

  @Test
  void anAddedLotNeverMakesASelectionOlder_the665Case() {
    // 700 m with 5% (665–735 m). The newer lot alone gives 700 m. A short piece of the older lot
    // with 700 m of the newer gives 735 m: it adds a lot, which makes the selection neither
    // shallower nor older, and it loses on fewer lots. The older lot is too deep to close within
    // the tolerance; closing a lot would rightly come first.
    Lot older =
        lot(
            shortPiece(35),
            normal(100),
            normal(100),
            normal(100),
            normal(100),
            normal(100),
            normal(100),
            normal(100),
            normal(100));
    Lot newer = lot(100, 100, 100, 100, 100, 100, 100, 100, 100, 100);

    Selection best =
        ProposalSearch.bestWithin(
                List.of(window(older, 0), window(newer, 1)), 665 * M, 735 * M, 700 * M)
            .best()
            .orElseThrow();

    assertThat(best.total()).isEqualTo(700 * M);
    assertThat(best.takes()).extracting(LotTake::lotId).containsExactly(newer.id());
  }

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

  private static Lot lot(Piece... pieces) {
    return new Lot(UUID.randomUUID(), List.of(pieces));
  }

  private static Piece normal(long metres) {
    return new Piece(UUID.randomUUID(), metres * M);
  }

  private static Piece shortPiece(long metres) {
    return new Piece(UUID.randomUUID(), metres * M, true);
  }

  private static List<Piece> pieces(long... metres) {
    List<Piece> pieces = new ArrayList<>();
    for (long length : metres) {
      pieces.add(new Piece(UUID.randomUUID(), length * M));
    }
    return pieces;
  }
}
