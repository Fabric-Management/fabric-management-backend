package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import static com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.*;
import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PaymentPlanGeneratorTest {
  @ParameterizedTest
  @CsvSource({
    "2026-10-30,20,2026-10-30,2026-11-20,2026-12-20",
    "2026-10-30,15,2026-10-30,2026-11-15,2026-12-15",
    "2027-01-31,31,2027-01-31,2027-02-28,2027-03-31",
    "2028-01-31,31,2028-01-31,2028-02-29,2028-03-31",
    "2028-02-29,20,2028-02-29,2028-03-20,2028-04-20"
  })
  void explicitFirstCollectionIsNeverMoved(
      String first, int anchor, String one, String two, String three) {
    var start = LocalDate.parse(first);
    assertThat(
            java.util.stream.IntStream.range(0, 3)
                .mapToObj(
                    index ->
                        PaymentPlanGenerator.collectionDate(
                            start, Frequency.MONTH, anchor, index, true))
                .toList())
        .containsExactly(LocalDate.parse(one), LocalDate.parse(two), LocalDate.parse(three));
  }

  @Test
  void weeklyDailyAndFallbackDatesKeepTheirMeaning() {
    var start = LocalDate.of(2026, 10, 30);
    assertThat(PaymentPlanGenerator.collectionDate(start, Frequency.WEEK, 15, 1, true))
        .isEqualTo("2026-11-06");
    assertThat(PaymentPlanGenerator.collectionDate(start, Frequency.DAY, 15, 1, true))
        .isEqualTo("2026-10-31");
    assertThat(
            PaymentPlanGenerator.collectionDate(
                LocalDate.of(2027, 1, 10), Frequency.MONTH, 31, 0, false))
        .isEqualTo("2027-01-31");
    assertThat(PaymentPlanGenerator.collectionDate(null, Frequency.MONTH, 20, 0, false)).isNull();
    assertThat(
            PaymentPlanGenerator.collectionDate(
                LocalDate.of(2027, 1, 31), Frequency.MONTH, null, 2, true))
        .isEqualTo("2027-03-31");
  }

  @Test
  void decimalGenerationIncludesRemainderAndNeverCombinesCurrencies() {
    var rules =
        new PaymentPlanInput.Rules(
            Frequency.MONTH,
            3,
            LocalDate.of(2026, 10, 30),
            20,
            Method.CHEQUE,
            new BigDecimal("10"),
            Method.CASH,
            DateTrigger.ON_ORDER_CONFIRMATION,
            null,
            ChequeDateMode.COMMON_DATE,
            LocalDate.of(2027, 7, 20));
    var input =
        new PaymentPlanInput(List.of(new PaymentPlanInput.CurrencyInput("GBP", rules, null)));
    var result =
        PaymentPlanGenerator.generate(
            input,
            Map.of("GBP", new BigDecimal("14280"), "USD", new BigDecimal("500")),
            false,
            LocalDate.of(2026, 10, 20));
    assertThat(result.currencyGroups()).hasSize(1);
    assertThat(result.currencyGroups().getFirst().lines())
        .extracting(PaymentPlanContent.Line::amount)
        .containsExactly(
            new BigDecimal("1428.00"),
            new BigDecimal("4284.00"),
            new BigDecimal("4284.00"),
            new BigDecimal("4284.00"));
    assertThat(result.currencyGroups().getFirst().lines().getFirst().expectedDate()).isNull();
    assertThat(result.validationIssues()).containsExactly("USD:PLAN_REQUIRED");
    var thirds =
        new PaymentPlanInput.Rules(
            Frequency.MONTH,
            3,
            LocalDate.of(2026, 10, 30),
            20,
            Method.CASH,
            null,
            null,
            null,
            null,
            null,
            null);
    var split =
        PaymentPlanGenerator.generate(
            new PaymentPlanInput(List.of(new PaymentPlanInput.CurrencyInput("GBP", thirds, null))),
            Map.of("GBP", new BigDecimal("100")),
            false,
            LocalDate.of(2026, 10, 20));
    assertThat(split.currencyGroups().getFirst().lines())
        .extracting(PaymentPlanContent.Line::amount)
        .containsExactly(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34"));
    assertThat(split.ready()).isTrue();
  }

  @Test
  void fixedDownPaymentAndOrderTermAreNotLostAsSummaryText() {
    var rules =
        new PaymentPlanInput.Rules(
            Frequency.MONTH,
            3,
            LocalDate.of(2026, 10, 30),
            20,
            Method.CHEQUE,
            null,
            Method.CASH,
            DateTrigger.ON_ORDER_CONFIRMATION,
            null,
            ChequeDateMode.COMMON_DATE,
            null,
            new BigDecimal("1428.00"),
            9,
            com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.OrderTermUnit
                .MONTH);
    var input =
        new PaymentPlanInput(List.of(new PaymentPlanInput.CurrencyInput("GBP", rules, null)));
    var result =
        PaymentPlanGenerator.generate(
            input, Map.of("GBP", new BigDecimal("14280")), false, LocalDate.of(2026, 10, 20));
    assertThat(result.ready()).isTrue();
    var rows = result.currencyGroups().getFirst().lines();
    assertThat(rows.getFirst().amount()).isEqualByComparingTo("1428.00");
    assertThat(rows.subList(1, 4))
        .extracting(PaymentPlanContent.Line::chequePaymentDate)
        .containsOnly(LocalDate.of(2027, 7, 20));
    assertThat(rows.subList(1, 4))
        .extracting(PaymentPlanContent.Line::amount)
        .containsOnly(new BigDecimal("4284.00"));
    assertThat(result.currencyGroups().getFirst().rules()).isEqualTo(rules);
  }

  @Test
  void manualRowsAndUnknownFactsRemainExplicit() {
    var row =
        new PaymentPlanInput.Row(
            null,
            Kind.INSTALMENT,
            new BigDecimal("99"),
            Method.CHEQUE,
            DateTrigger.EXPLICIT_DATE,
            LocalDate.of(2026, 10, 30),
            null);
    var input =
        new PaymentPlanInput(
            List.of(new PaymentPlanInput.CurrencyInput("GBP", null, List.of(row))));
    var result =
        PaymentPlanGenerator.generate(
            input, Map.of("GBP", new BigDecimal("100")), true, LocalDate.of(2026, 10, 20));
    assertThat(result.validationIssues())
        .contains("GBP:TOTAL_MISMATCH", "GBP:CHEQUE_DATE_REQUIRED:1", "UNPRICED_LINES");
    assertThat(result.currencyGroups().getFirst().lines().getFirst().expectedDate())
        .isEqualTo(row.expectedDate());
    assertThat(result.currencyGroups().getFirst().lines().getFirst().manual()).isTrue();
  }

  @Test
  void invalidPrecisionAndDuplicateCurrencyAreRefusedInsteadOfRounded() {
    var row =
        new PaymentPlanInput.Row(
            null,
            Kind.INSTALMENT,
            new BigDecimal("1.001"),
            Method.CASH,
            DateTrigger.EXPLICIT_DATE,
            LocalDate.of(2026, 10, 30),
            null);
    assertThatThrownBy(
            () ->
                PaymentPlanGenerator.generate(
                    new PaymentPlanInput(
                        List.of(new PaymentPlanInput.CurrencyInput("GBP", null, List.of(row)))),
                    Map.of(),
                    false,
                    null))
        .hasMessageContaining("precision");
    var group = new PaymentPlanInput.CurrencyInput("GBP", null, List.of());
    assertThatThrownBy(
            () ->
                PaymentPlanGenerator.generate(
                    new PaymentPlanInput(List.of(group, group)), Map.of(), false, null))
        .hasMessageContaining("only once");
  }

  @Test
  void indivisiblePenniesNeverProduceANegativeFinalInstalment() {
    var rule =
        new PaymentPlanInput.Rules(
            Frequency.MONTH,
            5,
            LocalDate.parse("2026-10-30"),
            20,
            Method.BANK_TRANSFER,
            null,
            null,
            null,
            null,
            null,
            null);
    var result =
        PaymentPlanGenerator.generate(
            new PaymentPlanInput(List.of(new PaymentPlanInput.CurrencyInput("GBP", rule, null))),
            Map.of("GBP", new BigDecimal("0.03")),
            false,
            LocalDate.parse("2026-10-20"));
    assertThat(result.currencyGroups().getFirst().lines())
        .extracting(PaymentPlanContent.Line::amount)
        .containsExactly(
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.01"),
            new BigDecimal("0.01"),
            new BigDecimal("0.01"));
    assertThat(result.ready()).isTrue();
  }

  private static PaymentPlanInput.Rules cashRules(int count, String downPaymentPercent) {
    return new PaymentPlanInput.Rules(
        Frequency.MONTH,
        count,
        LocalDate.of(2026, 10, 30),
        20,
        Method.CASH,
        downPaymentPercent == null ? null : new BigDecimal(downPaymentPercent),
        downPaymentPercent == null ? null : Method.CASH,
        downPaymentPercent == null ? null : DateTrigger.ON_ORDER_CONFIRMATION,
        null,
        null,
        null);
  }

  private static List<BigDecimal> amounts(
      String currency, PaymentPlanInput.Rules rules, String base) {
    return PaymentPlanGenerator.generate(
            new PaymentPlanInput(
                List.of(new PaymentPlanInput.CurrencyInput(currency, rules, null))),
            Map.of(currency, new BigDecimal(base)),
            false,
            LocalDate.of(2026, 10, 20))
        .currencyGroups()
        .getFirst()
        .lines()
        .stream()
        .map(PaymentPlanContent.Line::amount)
        .toList();
  }

  /** The same vectors are asserted by the frontend preview (payment-plan.test.ts). */
  @Test
  void amountsFollowTheCurrencyPrecisionRatherThanAFixedTwoDecimals() {
    assertThat(amounts("JPY", cashRules(3, null), "1000"))
        .containsExactly(new BigDecimal("333"), new BigDecimal("333"), new BigDecimal("334"));
    assertThat(amounts("JPY", cashRules(5, null), "3"))
        .containsExactly(
            new BigDecimal("0"),
            new BigDecimal("0"),
            new BigDecimal("1"),
            new BigDecimal("1"),
            new BigDecimal("1"));
    assertThat(amounts("JPY", cashRules(2, "15"), "1005"))
        .containsExactly(new BigDecimal("151"), new BigDecimal("427"), new BigDecimal("427"));
    assertThat(amounts("KWD", cashRules(7, "10"), "100"))
        .containsExactly(
            new BigDecimal("10.000"),
            new BigDecimal("12.857"),
            new BigDecimal("12.857"),
            new BigDecimal("12.857"),
            new BigDecimal("12.857"),
            new BigDecimal("12.857"),
            new BigDecimal("12.857"),
            new BigDecimal("12.858"));
  }
}
