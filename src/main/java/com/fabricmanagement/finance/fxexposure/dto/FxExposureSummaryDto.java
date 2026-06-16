package com.fabricmanagement.finance.fxexposure.dto;

import com.fabricmanagement.finance.common.dto.FinanceWarningDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FxExposureSummaryDto(
    LocalDate asOfDate,
    String reportingCurrency,
    BigDecimal totalNetReportingExposure,
    List<FxExposureCurrencyDto> openExposure,
    BigDecimal totalRealizedGainLoss,
    List<RealizedFxCurrencyDto> realizedFx,
    LocalDate latestRevaluationDate,
    BigDecimal totalUnrealizedGainLoss,
    List<UnrealizedFxCurrencyDto> unrealizedFx,
    List<FinanceWarningDto> warnings) {}
