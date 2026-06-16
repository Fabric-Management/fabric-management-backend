package com.fabricmanagement.analytics.dto;

import com.fabricmanagement.common.util.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrderMarginDto(
    UUID orderId,
    String orderNumber,
    UUID tradingPartnerId,
    UUID quoteId,
    LocalDate orderDate,
    Money revenue,
    Money estimatedCost,
    Money estimatedMargin,
    BigDecimal estimatedMarginPercentage,
    boolean costIncomplete,
    List<MarginWarningDto> warnings) {}
