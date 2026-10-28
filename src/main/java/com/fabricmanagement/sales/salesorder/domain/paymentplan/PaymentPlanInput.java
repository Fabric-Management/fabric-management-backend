package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.ChequeDateMode;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.DateTrigger;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Frequency;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Kind;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Method;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.OrderTermUnit;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Typed draft instructions. Null rows means generate; an explicit list preserves manual rows. */
@Schema(name = "SalesOrderPaymentPlanInput")
public record PaymentPlanInput(
    @NotNull @Size(max = 20) List<@NotNull @Valid CurrencyInput> currencyGroups) {
  public PaymentPlanInput {
    currencyGroups = currencyGroups == null ? null : List.copyOf(currencyGroups);
  }

  @Schema(name = "SalesOrderPaymentPlanCurrencyInput")
  public record CurrencyInput(
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
      @Valid @Schema(nullable = true) Rules rules,
      @Size(max = 121) List<@NotNull @Valid Row> rows) {
    public CurrencyInput {
      rows = rows == null ? null : List.copyOf(rows);
    }
  }

  @Schema(name = "SalesOrderPaymentPlanRules")
  public record Rules(
      @Schema(nullable = true) Frequency frequency,
      @Min(1) @Max(120) @Schema(nullable = true) Integer count,
      @Schema(nullable = true) LocalDate firstCollectionDate,
      @Min(1) @Max(31) @Schema(nullable = true) Integer anchorDay,
      @Schema(nullable = true) Method method,
      @DecimalMin("0")
          @DecimalMax("100")
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(nullable = true, type = "string", pattern = "[0-9]+(\\.[0-9]+)?", example = "10")
          BigDecimal downPaymentPercent,
      @Schema(nullable = true) Method downPaymentMethod,
      @Schema(nullable = true) DateTrigger downPaymentTrigger,
      @Schema(nullable = true) LocalDate downPaymentDate,
      @Schema(nullable = true) ChequeDateMode chequeDateMode,
      @Schema(nullable = true) LocalDate commonChequePaymentDate,
      @DecimalMin("0")
          @Digits(integer = 15, fraction = 4)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(nullable = true, type = "string", example = "1428.00")
          BigDecimal downPaymentAmount,
      @Min(1) @Max(3650) @Schema(nullable = true) Integer orderTermValue,
      @Schema(nullable = true) OrderTermUnit orderTermUnit) {
    public Rules(
        Frequency frequency,
        Integer count,
        LocalDate firstCollectionDate,
        Integer anchorDay,
        Method method,
        BigDecimal downPaymentPercent,
        Method downPaymentMethod,
        DateTrigger downPaymentTrigger,
        LocalDate downPaymentDate,
        ChequeDateMode chequeDateMode,
        LocalDate commonChequePaymentDate) {
      this(
          frequency,
          count,
          firstCollectionDate,
          anchorDay,
          method,
          downPaymentPercent,
          downPaymentMethod,
          downPaymentTrigger,
          downPaymentDate,
          chequeDateMode,
          commonChequePaymentDate,
          null,
          null,
          null);
    }
  }

  @Schema(name = "SalesOrderPaymentPlanRowInput")
  public record Row(
      @Schema(nullable = true) UUID obligationId,
      @NotNull Kind kind,
      @DecimalMin("0")
          @Digits(integer = 15, fraction = 4)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          @Schema(nullable = true, type = "string", example = "4284.00")
          BigDecimal amount,
      @Schema(nullable = true) Method method,
      @Schema(nullable = true) DateTrigger dateTrigger,
      @Schema(nullable = true) LocalDate expectedDate,
      @Schema(nullable = true) LocalDate chequePaymentDate) {}
}
