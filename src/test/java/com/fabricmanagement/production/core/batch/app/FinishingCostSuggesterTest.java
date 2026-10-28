package com.fabricmanagement.production.core.batch.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFinishingCategory;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingBillingBasis;
import com.fabricmanagement.product.fabric.domain.vocabulary.FinishingChargeUnit;
import com.fabricmanagement.product.fabric.dto.FabricFinishingPriceDto;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService.QualityGradeReference;
import com.fabricmanagement.production.core.batch.domain.FinishingCostLineStatus;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeBreakdown;
import com.fabricmanagement.production.core.batch.domain.FinishingGradeMetres;
import com.fabricmanagement.production.core.batch.dto.BatchFinishingDtos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinishingCostSuggesterTest {

  private static final LocalDate DAY = LocalDate.of(2026, 10, 1);
  private static final BigDecimal SENT = new BigDecimal("3000");
  private static final BigDecimal RETURNED = new BigDecimal("2940");

  private static final QualityGradeReference FIRST = grade("1A", 1);
  private static final QualityGradeReference SECOND = grade("2A", 2);
  private static final QualityGradeReference BAD = grade("B", 3);
  private static final Map<UUID, QualityGradeReference> GRADES =
      Map.of(FIRST.id(), FIRST, SECOND.id(), SECOND, BAD.id(), BAD);

  private static QualityGradeReference grade(String code, int rank) {
    return new QualityGradeReference(
        UUID.randomUUID(), code, "Grade " + code, rank, BigDecimal.ONE, true, true);
  }

  private static FinishingGradeBreakdown split(Object... pairs) {
    java.util.ArrayList<FinishingGradeMetres> rows = new java.util.ArrayList<>();
    for (int i = 0; i < pairs.length; i += 2) {
      rows.add(
          new FinishingGradeMetres(
              ((QualityGradeReference) pairs[i]).id(), new BigDecimal((String) pairs[i + 1])));
    }
    return FinishingGradeBreakdown.of(rows);
  }

  private static FabricFinishingPriceDto price(
      QualityGradeReference grade,
      FinishingBillingBasis basis,
      FinishingChargeUnit unit,
      String unitPrice,
      String currency) {
    return new FabricFinishingPriceDto(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "DYEING_REACTIVE",
        "Reactive dyeing",
        FabricFinishingCategory.DYEING,
        UUID.randomUUID(),
        basis,
        grade == null ? null : grade.id(),
        grade == null ? null : grade.code(),
        grade == null ? null : grade.name(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        unit,
        new BigDecimal(unitPrice),
        currency,
        DAY);
  }

  private static FabricFinishingPriceDto returned(
      QualityGradeReference grade, String unitPrice, String currency) {
    return price(grade, FinishingBillingBasis.RETURNED, FinishingChargeUnit.M, unitPrice, currency);
  }

  private static BatchFinishingDtos.CostSuggestionDto suggest(
      FinishingGradeBreakdown breakdown, FabricFinishingPriceDto... prices) {
    return FinishingCostSuggester.suggest(DAY, SENT, RETURNED, breakdown, List.of(prices), GRADES);
  }

  @Test
  void anUnsplitDeliveryIsOneLinePricedByThePriceWithoutAGrade() {
    BatchFinishingDtos.CostSuggestionDto suggestion =
        suggest(FinishingGradeBreakdown.none(), returned(null, "1.85", "GBP"));

    assertThat(suggestion.priceDate()).isEqualTo(DAY);
    assertThat(suggestion.complete()).isTrue();
    assertThat(suggestion.lines()).hasSize(1);
    BatchFinishingDtos.CostSuggestionLineDto line = suggestion.lines().get(0);
    assertThat(line.qualityGradeId()).isNull();
    assertThat(line.status()).isEqualTo(FinishingCostLineStatus.PRICED);
    assertThat(line.metres()).isEqualByComparingTo("2940");
    assertThat(line.billedMetres()).isEqualByComparingTo("2940");
    assertThat(line.amount()).isEqualByComparingTo("5439.00");
    assertThat(suggestion.totals()).hasSize(1);
    assertThat(suggestion.totals().get(0).currency()).isEqualTo("GBP");
    assertThat(suggestion.totals().get(0).amount()).isEqualByComparingTo("5439.00");
  }

  @Test
  void eachGradeUsesItsOwnPriceElseThePriceWithoutAGradeAndAFreeGradeCostsNothing() {
    BatchFinishingDtos.CostSuggestionDto suggestion =
        suggest(
            split(BAD, "40", SECOND, "100", FIRST, "2800"),
            returned(null, "1.85", "GBP"),
            returned(SECOND, "1.20", "GBP"),
            returned(BAD, "0", "GBP"));

    assertThat(suggestion.lines())
        .extracting(BatchFinishingDtos.CostSuggestionLineDto::qualityGradeCode)
        .as("best grade first, whatever the order they were entered in")
        .containsExactly("1A", "2A", "B");
    assertThat(suggestion.lines().get(0).amount()).isEqualByComparingTo("5180.00");
    assertThat(suggestion.lines().get(1).amount()).isEqualByComparingTo("120.00");
    assertThat(suggestion.lines().get(2).status()).isEqualTo(FinishingCostLineStatus.PRICED);
    assertThat(suggestion.lines().get(2).amount()).isEqualByComparingTo("0.00");
    assertThat(suggestion.complete()).isTrue();
    assertThat(suggestion.totals().get(0).amount()).isEqualByComparingTo("5300.00");
  }

  @Test
  void aGradeTheFirmDoesNotPriceIsNotBilledWhenItPricesOtherGrades() {
    BatchFinishingDtos.CostSuggestionDto suggestion =
        suggest(split(FIRST, "2800", BAD, "140"), returned(FIRST, "1.85", "GBP"));

    assertThat(suggestion.lines().get(0).status()).isEqualTo(FinishingCostLineStatus.PRICED);
    BatchFinishingDtos.CostSuggestionLineDto notBilled = suggestion.lines().get(1);
    assertThat(notBilled.status()).isEqualTo(FinishingCostLineStatus.NOT_BILLED);
    assertThat(notBilled.amount()).isNull();
    assertThat(notBilled.unitPrice()).isNull();
    assertThat(suggestion.complete()).as("not billed is a known answer, not a gap").isTrue();
    assertThat(suggestion.totals().get(0).amount()).isEqualByComparingTo("5180.00");
  }

  @Test
  void anUnsplitDeliveryNeedsTheSplitWhenOnlyIndividualGradesArePriced() {
    BatchFinishingDtos.CostSuggestionDto suggestion =
        suggest(FinishingGradeBreakdown.none(), returned(FIRST, "1.85", "GBP"));

    assertThat(suggestion.lines().get(0).status()).isEqualTo(FinishingCostLineStatus.NO_PRICE);
    assertThat(suggestion.complete()).isFalse();
    assertThat(suggestion.totals()).isEmpty();
  }

  @Test
  void nothingPricedMeansNoSuggestionForAnyLine() {
    BatchFinishingDtos.CostSuggestionDto suggestion = suggest(split(FIRST, "2800", SECOND, "140"));

    assertThat(suggestion.lines())
        .extracting(BatchFinishingDtos.CostSuggestionLineDto::status)
        .containsOnly(FinishingCostLineStatus.NO_PRICE);
    assertThat(suggestion.complete()).isFalse();
    assertThat(suggestion.totals()).isEmpty();
  }

  @Test
  void aPricePerKilogramCannotBeWorkedOutBecauseTheLotHasNoWeight() {
    BatchFinishingDtos.CostSuggestionDto suggestion =
        suggest(
            FinishingGradeBreakdown.none(),
            price(null, FinishingBillingBasis.RETURNED, FinishingChargeUnit.KG, "3.10", "GBP"));

    BatchFinishingDtos.CostSuggestionLineDto line = suggestion.lines().get(0);
    assertThat(line.status()).isEqualTo(FinishingCostLineStatus.NOT_COMPUTABLE);
    assertThat(line.unit()).isEqualTo(FinishingChargeUnit.KG);
    assertThat(line.unitPrice()).isEqualByComparingTo("3.10");
    assertThat(line.amount()).isNull();
    assertThat(suggestion.complete()).isFalse();
    assertThat(suggestion.totals()).isEmpty();
  }

  @Test
  void aPriceOnSentMetresIsSharedOutByEachGradesPartOfTheReturn() {
    BatchFinishingDtos.CostSuggestionDto suggestion =
        suggest(
            split(FIRST, "2800", SECOND, "140"),
            price(null, FinishingBillingBasis.SENT, FinishingChargeUnit.M, "1.50", "GBP"));

    BatchFinishingDtos.CostSuggestionLineDto first = suggestion.lines().get(0);
    BatchFinishingDtos.CostSuggestionLineDto second = suggestion.lines().get(1);
    assertThat(first.billedMetres()).isEqualByComparingTo("2857.143");
    assertThat(first.amount()).isEqualByComparingTo("4285.71");
    assertThat(second.billedMetres()).isEqualByComparingTo("142.857");
    assertThat(second.amount()).isEqualByComparingTo("214.29");
    assertThat(suggestion.totals().get(0).amount())
        .as("the firm bills the 3000 m it was sent, at 1.50")
        .isEqualByComparingTo("4500.00");
  }

  @Test
  void aPriceOnSentMetresOnAnUnsplitDeliveryBillsTheWholeSentLength() {
    BatchFinishingDtos.CostSuggestionDto suggestion =
        suggest(
            FinishingGradeBreakdown.none(),
            price(null, FinishingBillingBasis.SENT, FinishingChargeUnit.M, "1.50", "GBP"));

    assertThat(suggestion.lines().get(0).billedMetres()).isEqualByComparingTo("3000");
    assertThat(suggestion.lines().get(0).amount()).isEqualByComparingTo("4500.00");
  }

  @Test
  void pricesInDifferentCurrenciesAreTotalledSeparately() {
    BatchFinishingDtos.CostSuggestionDto suggestion =
        suggest(
            split(FIRST, "2800", SECOND, "140"),
            returned(FIRST, "1.85", "GBP"),
            returned(SECOND, "2.00", "EUR"));

    assertThat(suggestion.totals())
        .extracting(
            BatchFinishingDtos.CostSuggestionTotalDto::currency,
            total -> total.amount().toPlainString())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("EUR", "280.00"),
            org.assertj.core.groups.Tuple.tuple("GBP", "5180.00"));
  }
}
