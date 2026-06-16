package com.fabricmanagement.finance.period.dto;

import com.fabricmanagement.finance.period.domain.FinancialPeriod;
import com.fabricmanagement.finance.period.domain.FinancialPeriodStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialPeriodDto(
    UUID id,
    int periodYear,
    int periodMonth,
    LocalDate startDate,
    LocalDate endDate,
    FinancialPeriodStatus status,
    Instant closedAt,
    UUID closedBy,
    Instant reopenedAt,
    UUID reopenedBy) {

  public static FinancialPeriodDto from(FinancialPeriod period) {
    return new FinancialPeriodDto(
        period.getId(),
        period.getPeriodYear(),
        period.getPeriodMonth(),
        period.getStartDate(),
        period.getEndDate(),
        period.getStatus(),
        period.getClosedAt(),
        period.getClosedBy(),
        period.getReopenedAt(),
        period.getReopenedBy());
  }
}
