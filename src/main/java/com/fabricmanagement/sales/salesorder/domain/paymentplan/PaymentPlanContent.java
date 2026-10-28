package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.DateTrigger;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Kind;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Method;
import com.fasterxml.jackson.annotation.JsonFormat;
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

  @Schema(name = "SalesOrderPaymentPlanCurrency")
  public record CurrencyGroup(
      String currency,
      @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", nullable = true)
          BigDecimal baseAmount,
      @Schema(nullable = true) PaymentPlanInput.Rules rules,
      List<Line> lines) {
    public CurrencyGroup {
      lines = List.copyOf(lines);
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
      boolean manual) {}
}
