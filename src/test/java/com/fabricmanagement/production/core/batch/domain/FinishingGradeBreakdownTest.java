package com.fabricmanagement.production.core.batch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.production.core.batch.domain.exception.BatchDomainException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinishingGradeBreakdownTest {

  private static final UUID FIRST = UUID.randomUUID();
  private static final UUID SECOND = UUID.randomUUID();

  private static FinishingGradeMetres row(UUID grade, String metres) {
    return new FinishingGradeMetres(grade, metres == null ? null : new BigDecimal(metres));
  }

  private static void assertRejected(Runnable action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BatchDomainException.class)
        .extracting(error -> ((BatchDomainException) error).getErrorCode())
        .isEqualTo(code);
  }

  @Test
  void noSplitIsEmptyAndNeedsNothingToAddUp() {
    FinishingGradeBreakdown none = FinishingGradeBreakdown.of(null);

    assertThat(none.isEmpty()).isTrue();
    assertThat(none.total()).isEqualByComparingTo("0");
    assertThatCode(() -> none.requireTotal(new BigDecimal("2940"))).doesNotThrowAnyException();
    assertThat(FinishingGradeBreakdown.of(List.of()).isEmpty()).isTrue();
    assertThat(FinishingGradeBreakdown.none().isEmpty()).isTrue();
  }

  @Test
  void aSplitKeepsItsRowsAtThreeDecimalsAndAddsThemUp() {
    FinishingGradeBreakdown split =
        FinishingGradeBreakdown.of(List.of(row(FIRST, "2800"), row(SECOND, "140.5")));

    assertThat(split.isEmpty()).isFalse();
    assertThat(split.rows()).hasSize(2);
    assertThat(split.rows().get(1).metres().scale()).isEqualTo(3);
    assertThat(split.total()).isEqualByComparingTo("2940.5");
  }

  @Test
  void aSplitThatAddsUpToTheReturnedMetresPassesWhateverTheScale() {
    FinishingGradeBreakdown split =
        FinishingGradeBreakdown.of(List.of(row(FIRST, "2800"), row(SECOND, "140")));

    assertThatCode(() -> split.requireTotal(new BigDecimal("2940.000"))).doesNotThrowAnyException();
    assertThatCode(() -> split.requireTotal(new BigDecimal("2940"))).doesNotThrowAnyException();
  }

  @Test
  void aSplitThatDoesNotAccountForTheWholeDeliveryIsRejected() {
    FinishingGradeBreakdown split =
        FinishingGradeBreakdown.of(List.of(row(FIRST, "2800"), row(SECOND, "100")));

    assertThatThrownBy(() -> split.requireTotal(new BigDecimal("2940")))
        .isInstanceOf(BatchDomainException.class)
        .hasMessageContaining("2900")
        .hasMessageContaining("2940")
        .extracting(error -> ((BatchDomainException) error).getErrorCode())
        .isEqualTo("FINISHING_GRADE_METRES_MISMATCH");
  }

  @Test
  void aGradeCannotAppearTwice() {
    assertRejected(
        () -> FinishingGradeBreakdown.of(List.of(row(FIRST, "100"), row(FIRST, "200"))),
        "FINISHING_GRADE_DUPLICATE");
  }

  @Test
  void aRowNeedsAGradeAndPositiveMetresWithAtMostThreeDecimals() {
    assertRejected(
        () -> FinishingGradeBreakdown.of(List.of(row(null, "100"))),
        "FINISHING_GRADE_METRES_INVALID");
    assertRejected(
        () -> FinishingGradeBreakdown.of(List.of(row(FIRST, null))),
        "FINISHING_GRADE_METRES_INVALID");
    assertRejected(
        () -> FinishingGradeBreakdown.of(List.of(row(FIRST, "0"))),
        "FINISHING_GRADE_METRES_INVALID");
    assertRejected(
        () -> FinishingGradeBreakdown.of(List.of(row(FIRST, "-5"))),
        "FINISHING_GRADE_METRES_INVALID");
    assertRejected(
        () -> FinishingGradeBreakdown.of(List.of(row(FIRST, "10.0001"))),
        "FINISHING_GRADE_METRES_INVALID");
  }
}
