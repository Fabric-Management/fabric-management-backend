package com.fabricmanagement.finance.common.app;

import com.fabricmanagement.common.infrastructure.tenant.TenantBusinessCalendarPort;
import com.fabricmanagement.finance.common.exception.FuturePostingDateException;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** One definition of "today" for every finance posting, in the tenant's business calendar. */
@Component
@RequiredArgsConstructor
public class FinancePostingDates {
  private final Clock clock;
  private final TenantBusinessCalendarPort calendar;

  public LocalDate today() {
    return LocalDate.now(clock.withZone(calendar.zone()));
  }

  /** Refuses a missing or future date; the period guard still decides whether the day is open. */
  public void assertNotFuture(LocalDate date) {
    if (date == null) throw new IllegalArgumentException("A posting date is required");
    LocalDate today = today();
    if (date.isAfter(today)) throw new FuturePostingDateException(date, today);
  }
}
