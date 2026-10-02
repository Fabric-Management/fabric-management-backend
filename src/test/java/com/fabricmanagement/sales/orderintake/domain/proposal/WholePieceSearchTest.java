package com.fabricmanagement.sales.orderintake.domain.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch.Lot;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch.Piece;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch.Result;
import com.fabricmanagement.sales.orderintake.domain.proposal.WholePieceSearch.Selection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Units are centimetres: 100 m = 10_000. Scenario data is fictional (SOI acceptance). */
class WholePieceSearchTest {

  private static final long M = 100;

  @Test
  void s03_500mRequestedButWholePiecesGive514m() {
    Lot p3 = lot(102, 103, 101, 104, 104);

    Result result = WholePieceSearch.search(List.of(p3), 500 * M, false);

    assertThat(result.exact()).isEmpty();
    assertThat(result.above()).get().extracting(Selection::total).isEqualTo(514 * M);
    assertThat(result.above().get().takes().getFirst().remainingPieces()).isZero();
    // Four pieces would leave a single remnant; the largest allowed below is three pieces.
    assertThat(result.below()).get().extracting(Selection::total).isEqualTo(311 * M);
  }

  @Test
  void s04_twoLotsOfThree100mPieces() {
    Lot p1 = lot(100, 100, 100);
    Lot p2 = lot(100, 100, 100);

    Result strict = WholePieceSearch.search(List.of(p1, p2), 500 * M, false);

    assertThat(strict.exact()).isEmpty();
    Selection above = strict.above().orElseThrow();
    assertThat(above.total()).isEqualTo(600 * M);
    assertThat(above.takes()).allSatisfy(take -> assertThat(take.remainingPieces()).isZero());
    Selection below = strict.below().orElseThrow();
    assertThat(below.total()).isEqualTo(400 * M);
    assertThat(below.lotCount()).isEqualTo(2);
    assertThat(below.leavesSingleRemnant()).isFalse();
    // Older lot first: the first lot is emptied, the second keeps two pieces.
    assertThat(below.takes().getFirst().lotId()).isEqualTo(p1.id());
    assertThat(below.takes().getFirst().remainingPieces()).isZero();
    assertThat(below.takes().get(1).remainingPieces()).isEqualTo(2);

    Result relaxed = WholePieceSearch.search(List.of(p1, p2), 500 * M, true);
    assertThat(relaxed.exact())
        .get()
        .satisfies(
            selection -> {
              assertThat(selection.total()).isEqualTo(500 * M);
              assertThat(selection.leavesSingleRemnant()).isTrue();
            });
  }

  @Test
  void s16_emptyingALotThatHoldsOnePieceIsNotARemnant() {
    Lot p4 = lot(100);
    Lot p1 = lot(100, 100, 100);

    Result result = WholePieceSearch.search(List.of(p4, p1), 400 * M, false);

    assertThat(result.exact())
        .get()
        .satisfies(
            selection -> {
              assertThat(selection.lotCount()).isEqualTo(2);
              assertThat(selection.takes())
                  .allSatisfy(take -> assertThat(take.remainingPieces()).isZero());
            });
  }

  @Test
  void fewerLotsWinForTheSameTotal() {
    Lot a = lot(100, 100, 100, 100);
    Lot b = lot(200, 50, 50);

    Result result = WholePieceSearch.search(List.of(a, b), 400 * M, false);

    assertThat(result.exact()).get().extracting(Selection::lotCount).isEqualTo(1);
    assertThat(result.exact().get().takes().getFirst().lotId()).isEqualTo(a.id());
  }

  @Test
  void selectedPiecesAddUpToTheReportedTotal() {
    Lot a = lot(95.5, 101.25, 99, 103.75, 100.5, 98);

    Result result = WholePieceSearch.search(List.of(a), 300 * M, false);

    for (Selection selection :
        List.of(result.below().orElseThrow(), result.above().orElseThrow())) {
      long sum =
          a.pieces().stream()
              .filter(piece -> selection.takes().getFirst().pieceIds().contains(piece.id()))
              .mapToLong(Piece::units)
              .sum();
      assertThat(sum).isEqualTo(selection.total());
    }
  }

  @Test
  void aProblemAboveTheLimitsIsRefusedNotApproximated() {
    double[] lengths =
        IntStream.range(0, WholePieceSearch.MAX_PIECES_PER_LOT + 1).mapToDouble(i -> 100).toArray();

    Result result = WholePieceSearch.search(List.of(lot(lengths)), 500 * M, false);

    assertThat(result.limitExceeded()).isTrue();
    assertThat(result.exact()).isEmpty();
    assertThat(result.above()).isEmpty();
  }

  @Test
  void noPiecesMeansNoSelections() {
    Result result =
        WholePieceSearch.search(List.of(new Lot(UUID.randomUUID(), List.of())), 500 * M, false);

    assertThat(result.exact()).isEmpty();
    assertThat(result.above()).isEmpty();
    assertThat(result.below()).isEmpty();
    assertThat(result.limitExceeded()).isFalse();
  }

  private static Lot lot(double... metres) {
    List<Piece> pieces = new ArrayList<>();
    for (double length : metres) {
      pieces.add(new Piece(UUID.randomUUID(), Math.round(length * M)));
    }
    return new Lot(UUID.randomUUID(), pieces);
  }
}
