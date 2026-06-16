package com.fabricmanagement.finance.period.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReopenFinancialPeriodResponse(
    FinancialPeriodDto period,
    int reversalEntryCount,
    BigDecimal reversalMovement,
    List<FxRevaluationDto> entries) {}
