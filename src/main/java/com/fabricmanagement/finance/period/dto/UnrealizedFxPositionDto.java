package com.fabricmanagement.finance.period.dto;

import java.math.BigDecimal;
import java.util.List;

public record UnrealizedFxPositionDto(
    FinancialPeriodDto period, BigDecimal unrealizedFxPosition, List<FxRevaluationDto> entries) {}
