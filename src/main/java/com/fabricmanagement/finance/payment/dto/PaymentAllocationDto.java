package com.fabricmanagement.finance.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentAllocationDto(
    UUID id,
    UUID paymentId,
    UUID invoiceId,
    BigDecimal amount,
    String currency,
    Instant allocatedAt,
    String reportingCurrency,
    BigDecimal realizedFxGainLoss,
    BigDecimal settlementExchangeRate,
    LocalDate settlementExchangeRateDate) {}
