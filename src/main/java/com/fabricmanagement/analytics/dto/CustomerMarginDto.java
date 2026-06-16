package com.fabricmanagement.analytics.dto;

import com.fabricmanagement.common.util.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CustomerMarginDto(
    UUID tradingPartnerId,
    String tradingPartnerName,
    Money totalRevenue,
    Money totalEstimatedCost,
    Money totalEstimatedMargin,
    BigDecimal estimatedMarginPercentage,
    int orderCount,
    boolean costIncomplete,
    List<MarginWarningDto> warnings) {}
