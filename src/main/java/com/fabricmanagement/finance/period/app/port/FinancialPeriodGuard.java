package com.fabricmanagement.finance.period.app.port;

import java.time.LocalDate;
import java.util.UUID;

public interface FinancialPeriodGuard {

  void assertPostingAllowed(UUID tenantId, LocalDate accountingDate);
}
