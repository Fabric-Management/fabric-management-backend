package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.DateTrigger;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Kind;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Method;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Frozen commercial facts. Dates triggered by confirmation are not replaced with today's date. */
@Schema(name = "SalesOrderPaymentPlanContent")
public record PaymentPlanContent(
    @Schema(nullable = true) UUID revisionId,
    int revisionNo,
    String pricingFingerprint,
    List<CurrencyGroup> currencyGroups,
    List<String> validationIssues) {
  public PaymentPlanContent {
    currencyGroups = List.copyOf(currencyGroups);
    validationIssues = List.copyOf(validationIssues);
  }

  public boolean ready() {
    return validationIssues.isEmpty();
  }

  /**
   * {@code ownChequePercent}: agreed own share of the total paid by cheque, 0–100; null = no term
   * recorded (never read as 100). Omitted from JSON when null so order-version hashes of plans
   * without a term are unchanged.
   */
  @Schema(name = "SalesOrderPaymentPlanCurrency")
  public record CurrencyGroup(
      String currency,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", nullable = true)
          BigDecimal baseAmount,
      @Schema(nullable = true) PaymentPlanInput.Rules rules,
      List<Line> lines,
      @JsonInclude(JsonInclude.Include.NON_NULL)
          @Schema(nullable = true, minimum = "0", maximum = "100")
          Integer ownChequePercent) {
    public CurrencyGroup {
      lines = List.copyOf(lines);
    }

    public CurrencyGroup(
        String currency, BigDecimal baseAmount, PaymentPlanInput.Rules rules, List<Line> lines) {
      this(currency, baseAmount, rules, lines, null);
    }

    /** Whether any row of this currency is paid by cheque (PAYMENT-PLAN-7 R6). */
    public boolean hasChequeRow() {
      return lines.stream().anyMatch(Line::paidByCheque);
    }
  }

  @Schema(name = "SalesOrderPaymentPlanLine")
  public record Line(
      UUID obligationId,
      int ordinal,
      Kind kind,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", nullable = true)
          BigDecimal amount,
      @Schema(nullable = true) Method method,
      @Schema(nullable = true) DateTrigger dateTrigger,
      @Schema(nullable = true) LocalDate expectedDate,
      @Schema(nullable = true) LocalDate chequePaymentDate,
      boolean manual) {
    /**
     * A cheque row: any row whose method is cheque, whatever its amount. The form shows the split
     * control for exactly these rows, so the backend never asks for a term the form cannot offer.
     */
    public boolean paidByCheque() {
      return method == Method.CHEQUE;
    }
  }
}
