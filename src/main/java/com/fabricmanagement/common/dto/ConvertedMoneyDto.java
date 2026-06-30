package com.fabricmanagement.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Money converted from an original currency into a reporting currency")
public class ConvertedMoneyDto {

  @Schema(description = "Original amount before conversion")
  BigDecimal originalAmount;

  @Schema(description = "Original currency code")
  String originalCurrency;

  @Schema(description = "Converted amount")
  BigDecimal convertedAmount;

  @Schema(description = "Converted currency code")
  String convertedCurrency;

  @Schema(description = "Exchange rate used for the conversion")
  BigDecimal exchangeRate;

  @Schema(description = "Rate date used for the conversion")
  LocalDate rateDate;
}
