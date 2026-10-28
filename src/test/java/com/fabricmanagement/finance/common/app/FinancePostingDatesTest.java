package com.fabricmanagement.finance.common.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.tenant.TenantBusinessCalendarPort;
import com.fabricmanagement.finance.common.exception.FuturePostingDateException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FinancePostingDatesTest {

  /** 23:30 UTC on 22 Oct: already 23 Oct in London (BST), still 22 Oct in New York. */
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T23:30:00Z"), ZoneOffset.UTC);

  private FinancePostingDates in(String zone) {
    TenantBusinessCalendarPort calendar = mock(TenantBusinessCalendarPort.class);
    when(calendar.zone()).thenReturn(ZoneId.of(zone));
    return new FinancePostingDates(clock, calendar);
  }

  @Test
  void todayIsTheDayInTheTenantsZoneNotTheServers() {
    assertThat(in("Europe/London").today()).isEqualTo(LocalDate.of(2026, 10, 23));
    assertThat(in("America/New_York").today()).isEqualTo(LocalDate.of(2026, 10, 22));
  }

  @Test
  void todayAndEarlierAreAccepted() {
    var dates = in("Europe/London");
    assertThatCode(() -> dates.assertNotFuture(LocalDate.of(2026, 10, 23)))
        .doesNotThrowAnyException();
    assertThatCode(() -> dates.assertNotFuture(LocalDate.of(2025, 1, 1)))
        .doesNotThrowAnyException();
  }

  @Test
  void aDayAfterTheTenantsTodayIsRefusedEvenWhenItIsTodayOnTheServer() {
    var newYork = in("America/New_York");
    assertThatThrownBy(() -> newYork.assertNotFuture(LocalDate.of(2026, 10, 23)))
        .isInstanceOf(FuturePostingDateException.class)
        .satisfies(
            failure -> {
              var refusal = (FuturePostingDateException) failure;
              assertThat(refusal.getErrorCode()).isEqualTo("FINANCE_POSTING_DATE_IN_FUTURE");
              assertThat(refusal.getHttpStatus()).isEqualTo(400);
            });
  }

  @Test
  void aMissingDateIsAProgrammingErrorNotAPass() {
    var dates = in("Europe/London");
    assertThatThrownBy(() -> dates.assertNotFuture(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
