package com.fabricmanagement.finance.common.exception;

import java.time.LocalDate;

/** A posting dated after the tenant's today; a ledger records what happened, not what will. */
public final class FuturePostingDateException extends FinanceDomainException {

  public FuturePostingDateException(LocalDate date, LocalDate today) {
    super(
        "A posting cannot be dated in the future (" + date + " is after " + today + ")",
        "FINANCE_POSTING_DATE_IN_FUTURE",
        400);
  }
}
