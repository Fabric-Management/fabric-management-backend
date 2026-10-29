package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent.CurrencyGroup;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent.Line;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.ChequeDateMode;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.DateTrigger;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Frequency;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Kind;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative draft generation. Incomplete business facts are issues, malformed input is refused.
 */
public final class PaymentPlanGenerator {
  private PaymentPlanGenerator() {}

  public static PaymentPlanContent generate(
      PaymentPlanInput input,
      Map<String, BigDecimal> bases,
      boolean unpriced,
      LocalDate orderDate) {
    var issues = new ArrayList<String>();
    var groups = new ArrayList<CurrencyGroup>();
    var currencies = new HashSet<String>();
    var obligationIds = new HashSet<UUID>();
    if (input == null || input.currencyGroups() == null)
      throw invalid("Currency groups are required");
    if (input.currencyGroups().size() > 20) throw invalid("Too many currency groups");
    for (var group : input.currencyGroups()) {
      if (group == null || group.currency() == null || !group.currency().matches("[A-Z]{3}"))
        throw invalid("Choose a valid currency");
      int scale;
      try {
        scale = Currency.getInstance(group.currency()).getDefaultFractionDigits();
      } catch (IllegalArgumentException e) {
        throw invalid("Unknown currency");
      }
      if (scale < 0 || scale > 4) throw invalid("Unsupported currency precision");
      if (!currencies.add(group.currency())) throw invalid("A currency may appear only once");
      BigDecimal base = bases.get(group.currency());
      var rows =
          group.rows() == null
              ? automatic(group.rules(), base, orderDate, scale)
              : manual(group.rows(), scale);
      if (base == null) issues.add(group.currency() + ":BASE_UNKNOWN");
      if (rows.isEmpty()) issues.add(group.currency() + ":NO_ROWS");
      if (rows.stream().filter(line -> line.kind() == Kind.DOWN_PAYMENT).count() > 1)
        throw invalid("Only one down payment is allowed per currency");
      BigDecimal planned = BigDecimal.ZERO;
      LocalDate previous = null;
      var ids = new HashSet<UUID>();
      for (var line : rows) {
        if (!ids.add(line.obligationId()) || !obligationIds.add(line.obligationId()))
          throw invalid("Duplicate obligation identity");
        if (line.amount() == null)
          issues.add(group.currency() + ":AMOUNT_REQUIRED:" + line.ordinal());
        else planned = planned.add(line.amount());
        if (line.method() == null)
          issues.add(group.currency() + ":METHOD_REQUIRED:" + line.ordinal());
        if (line.dateTrigger() == null
            || (line.dateTrigger() == DateTrigger.EXPLICIT_DATE && line.expectedDate() == null))
          issues.add(group.currency() + ":DATE_REQUIRED:" + line.ordinal());
        if (line.kind() == Kind.INSTALMENT
            && line.dateTrigger() == DateTrigger.ON_ORDER_CONFIRMATION)
          throw invalid("Only a down payment may be triggered by order confirmation");
        if (line.dateTrigger() == DateTrigger.ON_ORDER_CONFIRMATION && line.expectedDate() != null)
          throw invalid("A confirmation-triggered date must remain unresolved");
        if (line.kind() == Kind.INSTALMENT && line.expectedDate() != null) {
          if (previous != null && !line.expectedDate().isAfter(previous))
            issues.add(group.currency() + ":DATES_NOT_INCREASING");
          previous = line.expectedDate();
        }
        if (line.method() == Method.CHEQUE && line.chequePaymentDate() == null)
          issues.add(group.currency() + ":CHEQUE_DATE_REQUIRED:" + line.ordinal());
        if (line.method() == Method.CHEQUE
            && line.expectedDate() != null
            && line.chequePaymentDate() != null
            && line.chequePaymentDate().isBefore(line.expectedDate()))
          issues.add(group.currency() + ":CHEQUE_PAYMENT_BEFORE_RECEIPT:" + line.ordinal());
      }
      if (base != null && planned.compareTo(base) != 0)
        issues.add(group.currency() + ":TOTAL_MISMATCH");
      groups.add(new CurrencyGroup(group.currency(), base, group.rules(), rows));
    }
    for (String currency : bases.keySet())
      if (!currencies.contains(currency)) issues.add(currency + ":PLAN_REQUIRED");
    if (unpriced) issues.add("UNPRICED_LINES");
    if (groups.isEmpty()) issues.add("PLAN_EMPTY");
    return new PaymentPlanContent(null, 0, null, groups, issues);
  }

  private static List<Line> automatic(
      PaymentPlanInput.Rules rule, BigDecimal base, LocalDate orderDate, int scale) {
    if (rule == null) return List.of();
    if ((rule.count() != null && (rule.count() < 1 || rule.count() > 120))
        || (rule.anchorDay() != null && (rule.anchorDay() < 1 || rule.anchorDay() > 31)))
      throw invalid("Count or monthly day is outside its allowed range");
    BigDecimal percent = rule.downPaymentPercent();
    if (percent != null && (percent.signum() < 0 || percent.compareTo(new BigDecimal("100")) > 0))
      throw invalid("Down payment percentage must be between 0 and 100");
    BigDecimal fixedDown = rule.downPaymentAmount();
    if (fixedDown != null && (fixedDown.signum() < 0 || percent != null))
      throw invalid("Choose either an amount or a percentage for the down payment");
    if (fixedDown != null) {
      try {
        fixedDown = fixedDown.setScale(scale, RoundingMode.UNNECESSARY);
      } catch (ArithmeticException e) {
        throw invalid("Down payment exceeds currency precision");
      }
    }
    if (rule.orderTermValue() != null
        && (rule.orderTermValue() < 1 || rule.orderTermValue() > 3650))
      throw invalid("Order term is outside its allowed range");
    var result = new ArrayList<Line>();
    BigDecimal down = base == null ? null : BigDecimal.ZERO;
    if (percent != null || fixedDown != null) {
      down =
          fixedDown != null
              ? fixedDown
              : base == null
                  ? null
                  : base.multiply(percent).movePointLeft(2).setScale(scale, RoundingMode.HALF_UP);
      result.add(
          new Line(
              UUID.randomUUID(),
              0,
              Kind.DOWN_PAYMENT,
              down,
              rule.downPaymentMethod(),
              rule.downPaymentTrigger(),
              rule.downPaymentDate(),
              chequeDate(rule, rule.downPaymentMethod(), orderDate),
              false));
    }
    if (rule.count() == null) return result;
    BigDecimal remaining = base == null ? null : base.subtract(down).max(BigDecimal.ZERO);
    BigDecimal roundedEach =
        remaining == null
            ? null
            : remaining.divide(BigDecimal.valueOf(rule.count()), scale, RoundingMode.HALF_UP);
    boolean distribute =
        roundedEach != null
            && roundedEach.multiply(BigDecimal.valueOf(rule.count() - 1)).compareTo(remaining) > 0;
    BigDecimal each =
        distribute
            ? remaining.divide(BigDecimal.valueOf(rule.count()), scale, RoundingMode.DOWN)
            : roundedEach;
    // Retain the ordinary last-row rounding convention. For sub-penny-per-row cases distribute
    // indivisible minor units over the last rows instead of producing a negative final amount.
    int extraRows =
        !distribute
            ? 0
            : remaining
                .subtract(each.multiply(BigDecimal.valueOf(rule.count())))
                .movePointRight(scale)
                .intValueExact();
    for (int i = 0; i < rule.count(); i++) {
      LocalDate start = rule.firstCollectionDate() == null ? orderDate : rule.firstCollectionDate();
      LocalDate date =
          collectionDate(
              start, rule.frequency(), rule.anchorDay(), i, rule.firstCollectionDate() != null);
      BigDecimal amount =
          each == null
              ? null
              : distribute
                  ? (i >= rule.count() - extraRows
                      ? each.add(BigDecimal.ONE.movePointLeft(scale))
                      : each)
                  : i == rule.count() - 1
                      ? remaining.subtract(each.multiply(BigDecimal.valueOf(i)))
                      : each;
      result.add(
          new Line(
              UUID.randomUUID(),
              i + 1,
              Kind.INSTALMENT,
              amount,
              rule.method(),
              DateTrigger.EXPLICIT_DATE,
              date,
              chequeDate(rule, rule.method(), orderDate),
              false));
    }
    return result;
  }

  public static LocalDate collectionDate(
      LocalDate start, Frequency frequency, Integer day, int index, boolean explicitFirst) {
    if (start == null || frequency == null) return null;
    LocalDate date =
        switch (frequency) {
          case MONTH -> start.plusMonths(index);
          case WEEK -> start.plusWeeks(index);
          case DAY -> start.plusDays(index);
        };
    if (frequency == Frequency.MONTH && day != null && (index > 0 || !explicitFirst))
      date = date.withDayOfMonth(Math.min(day, date.lengthOfMonth()));
    return date;
  }

  private static LocalDate chequeDate(
      PaymentPlanInput.Rules rule, Method method, LocalDate orderDate) {
    if (method != Method.CHEQUE || rule.chequeDateMode() != ChequeDateMode.COMMON_DATE) return null;
    LocalDate derived =
        rule.orderTermValue() == null || rule.orderTermUnit() == null || orderDate == null
            ? null
            : switch (rule.orderTermUnit()) {
              case MONTH -> orderDate.plusMonths(rule.orderTermValue());
              case DAY -> orderDate.plusDays(rule.orderTermValue());
            };
    if (derived != null
        && rule.commonChequePaymentDate() != null
        && !derived.equals(rule.commonChequePaymentDate()))
      throw invalid("The common cheque date conflicts with the order term");
    return rule.commonChequePaymentDate() == null ? derived : rule.commonChequePaymentDate();
  }

  private static List<Line> manual(List<PaymentPlanInput.Row> rows, int scale) {
    if (rows.size() > 121) throw invalid("Too many plan rows");
    var result = new ArrayList<Line>();
    int ordinal = 0;
    for (var row : rows) {
      if (row == null || row.kind() == null) throw invalid("A row kind is required");
      BigDecimal amount = row.amount();
      if (amount != null) {
        if (amount.signum() < 0 || amount.precision() - amount.scale() > 15)
          throw invalid("Invalid row amount");
        try {
          amount = amount.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
          throw invalid("Row amount exceeds currency precision");
        }
      }
      result.add(
          new Line(
              row.obligationId() == null ? UUID.randomUUID() : row.obligationId(),
              row.kind() == Kind.DOWN_PAYMENT ? 0 : ++ordinal,
              row.kind(),
              amount,
              row.method(),
              row.dateTrigger(),
              row.expectedDate(),
              row.chequePaymentDate(),
              true));
    }
    if (ordinal > 120) throw invalid("Too many instalments");
    return result;
  }

  private static OrderDomainException invalid(String message) {
    return OrderDomainException.rule("PAYMENT_PLAN_INVALID", message);
  }
}
