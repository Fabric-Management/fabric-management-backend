package com.fabricmanagement.finance.period.dto;

import com.fabricmanagement.finance.fx.domain.FxRevaluation;
import com.fabricmanagement.finance.fx.domain.FxRevaluationEntryType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FxRevaluationDto(
    UUID id,
    UUID periodId,
    UUID invoiceId,
    FxRevaluationEntryType entryType,
    String invoiceSide,
    LocalDate asOfDate,
    BigDecimal openDocumentAmount,
    String documentCurrency,
    String reportingCurrency,
    BigDecimal issueExchangeRate,
    LocalDate issueExchangeRateDate,
    BigDecimal closingExchangeRate,
    LocalDate closingExchangeRateDate,
    BigDecimal unrealizedGainLoss,
    Instant revaluedAt,
    UUID reversalOfId) {

  public static FxRevaluationDto from(FxRevaluation revaluation) {
    return new FxRevaluationDto(
        revaluation.getId(),
        revaluation.getPeriodId(),
        revaluation.getInvoiceId(),
        revaluation.getEntryType(),
        revaluation.getInvoiceSide(),
        revaluation.getAsOfDate(),
        revaluation.getOpenDocumentAmount().getAmount(),
        revaluation.getOpenDocumentAmount().getCurrency().getCurrencyCode(),
        revaluation.getReportingCurrency(),
        revaluation.getIssueExchangeRate(),
        revaluation.getIssueExchangeRateDate(),
        revaluation.getClosingExchangeRate(),
        revaluation.getClosingExchangeRateDate(),
        revaluation.getUnrealizedGainLoss(),
        revaluation.getRevaluedAt(),
        revaluation.getReversalOfId());
  }
}
