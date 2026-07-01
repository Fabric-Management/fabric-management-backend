package com.fabricmanagement.common.dto;

import com.fabricmanagement.common.util.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Money amount with currency")
public class MoneyDto {

  @Schema(description = "Amount")
  BigDecimal amount;

  @Schema(description = "Currency code")
  String currency;

  public static MoneyDto from(Money money) {
    if (money == null) {
      return null;
    }
    return MoneyDto.builder()
        .amount(money.getAmount())
        .currency(money.getCurrency().getCurrencyCode())
        .build();
  }
}
