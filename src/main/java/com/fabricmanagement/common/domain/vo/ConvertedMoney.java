package com.fabricmanagement.common.domain.vo;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConvertedMoney {
  private BigDecimal originalAmount;
  private String originalCurrency; // USD
  private BigDecimal convertedAmount;
  private String convertedCurrency; // TRY (tenant reporting currency)
  private BigDecimal exchangeRate; // 38.50
  private LocalDate rateDate; // 2026-04-01

  /** Creates a converted money between different currencies. */
  public static ConvertedMoney of(
      BigDecimal originalAmount,
      String originalCurrency,
      BigDecimal convertedAmount,
      String convertedCurrency,
      BigDecimal exchangeRate,
      LocalDate rateDate) {
    return new ConvertedMoney(
        originalAmount, originalCurrency,
        convertedAmount, convertedCurrency,
        exchangeRate, rateDate);
  }

  /** Same currency — no conversion needed, rate=1. */
  public static ConvertedMoney sameUnit(BigDecimal amount, String currency) {
    return new ConvertedMoney(amount, currency, amount, currency, BigDecimal.ONE, LocalDate.now());
  }
}
