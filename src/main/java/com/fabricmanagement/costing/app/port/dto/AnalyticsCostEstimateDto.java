package com.fabricmanagement.costing.app.port.dto;

import com.fabricmanagement.common.util.Money;
import lombok.Builder;

@Builder
public record AnalyticsCostEstimateDto(Money totalCost, boolean complete) {}
