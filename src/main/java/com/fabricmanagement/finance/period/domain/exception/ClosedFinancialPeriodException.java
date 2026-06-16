package com.fabricmanagement.finance.period.domain.exception;

import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import java.time.LocalDate;
import java.time.YearMonth;

public class ClosedFinancialPeriodException extends FinanceDomainException {

  public static final String ERROR_CODE = "FINANCIAL_PERIOD_CLOSED";
  private static final int HTTP_CONFLICT = 409;

  public ClosedFinancialPeriodException(LocalDate accountingDate, YearMonth periodMonth) {
    super(
        "Posting date %s falls in closed financial period %s"
            .formatted(accountingDate, periodMonth),
        ERROR_CODE,
        HTTP_CONFLICT);
  }
}
