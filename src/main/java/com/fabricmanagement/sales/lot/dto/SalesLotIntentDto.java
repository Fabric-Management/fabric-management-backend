package com.fabricmanagement.sales.lot.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalesLotIntentDto(
    UUID quoteId,
    String quoteNumber,
    String marketerName,
    BigDecimal quantity,
    LocalDate expiresAt) {}
