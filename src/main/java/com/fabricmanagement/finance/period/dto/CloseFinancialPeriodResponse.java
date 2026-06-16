package com.fabricmanagement.finance.period.dto;

import java.math.BigDecimal;
import java.util.List;

public record CloseFinancialPeriodResponse(
    FinancialPeriodDto period,
    int reversedEntryCount,
    int revaluationEntryCount,
    BigDecimal periodMovement,
    List<FxRevaluationDto> entries) {}
