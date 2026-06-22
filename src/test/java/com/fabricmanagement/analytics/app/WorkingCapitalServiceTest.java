package com.fabricmanagement.analytics.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.analytics.dto.WorkingCapitalResponse;
import com.fabricmanagement.analytics.dto.WorkingCapitalWarningDto;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.finance.common.app.port.AnalyticsFinancePort;
import com.fabricmanagement.finance.payables.dto.DpoDto;
import com.fabricmanagement.finance.receivables.dto.DsoDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkingCapitalServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final String CURRENCY = "TRY";

  @Mock private AnalyticsFinancePort analyticsFinancePort;
  @Mock private TenantReportingCurrencyPort reportingCurrencyPort;

  private WorkingCapitalService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    Clock clock = Clock.fixed(Instant.parse("2026-06-19T10:00:00Z"), ZoneId.of("UTC"));
    service = new WorkingCapitalService(analyticsFinancePort, reportingCurrencyPort, clock);
    when(reportingCurrencyPort.getReportingCurrency(TENANT_ID)).thenReturn(CURRENCY);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private DsoDto dso(BigDecimal value, String status) {
    return new DsoDto(90, value, BigDecimal.valueOf(100000), BigDecimal.valueOf(200000), status);
  }

  private DpoDto dpo(BigDecimal value, String status) {
    return new DpoDto(90, value, BigDecimal.valueOf(50000), BigDecimal.valueOf(150000), status);
  }

  @Test
  void computesOperatingCashGapWhenBothPresent() {
    when(analyticsFinancePort.getDso(eq(TENANT_ID), any(), eq(CURRENCY)))
        .thenReturn(dso(BigDecimal.valueOf(42), null));
    when(analyticsFinancePort.getDpo(eq(TENANT_ID), any(), eq(CURRENCY)))
        .thenReturn(dpo(BigDecimal.valueOf(30), null));

    WorkingCapitalResponse res = service.getWorkingCapital(null);

    assertThat(res.operatingCashGapDays()).isEqualByComparingTo(BigDecimal.valueOf(12));
    assertThat(res.reportingCurrency()).isEqualTo(CURRENCY);
    assertThat(res.dioPending()).isTrue();
    assertThat(res.cccComplete()).isFalse();
    assertThat(res.warnings()).isEmpty();
  }

  @Test
  void okStatusProducesNoWarning() {
    when(analyticsFinancePort.getDso(eq(TENANT_ID), any(), eq(CURRENCY)))
        .thenReturn(dso(BigDecimal.valueOf(83), "OK"));
    when(analyticsFinancePort.getDpo(eq(TENANT_ID), any(), eq(CURRENCY)))
        .thenReturn(dpo(BigDecimal.valueOf(61), "OK"));

    WorkingCapitalResponse res = service.getWorkingCapital(null);

    assertThat(res.operatingCashGapDays()).isEqualByComparingTo(BigDecimal.valueOf(22));
    assertThat(res.warnings()).isEmpty();
  }

  @Test
  void gapNullAndWarningWhenDsoUnavailable() {
    when(analyticsFinancePort.getDso(eq(TENANT_ID), any(), eq(CURRENCY)))
        .thenReturn(dso(null, "INSUFFICIENT_SALES_WINDOW"));
    when(analyticsFinancePort.getDpo(eq(TENANT_ID), any(), eq(CURRENCY)))
        .thenReturn(dpo(BigDecimal.valueOf(30), null));

    WorkingCapitalResponse res = service.getWorkingCapital(null);

    assertThat(res.operatingCashGapDays()).isNull();
    assertThat(res.warnings())
        .extracting(WorkingCapitalWarningDto::code)
        .containsExactly("INSUFFICIENT_SALES_WINDOW");
  }

  @Test
  void gapNullAndWarningWhenDpoUnavailable() {
    when(analyticsFinancePort.getDso(eq(TENANT_ID), any(), eq(CURRENCY)))
        .thenReturn(dso(BigDecimal.valueOf(42), null));
    when(analyticsFinancePort.getDpo(eq(TENANT_ID), any(), eq(CURRENCY)))
        .thenReturn(dpo(null, "INSUFFICIENT_PURCHASE_WINDOW"));

    WorkingCapitalResponse res = service.getWorkingCapital(null);

    assertThat(res.operatingCashGapDays()).isNull();
    assertThat(res.warnings())
        .extracting(WorkingCapitalWarningDto::code)
        .containsExactly("INSUFFICIENT_PURCHASE_WINDOW");
  }

  @Test
  void bothUnavailableKeepsFlagsAndCollectsBothWarnings() {
    when(analyticsFinancePort.getDso(eq(TENANT_ID), any(), eq(CURRENCY)))
        .thenReturn(dso(null, "INSUFFICIENT_SALES_WINDOW"));
    when(analyticsFinancePort.getDpo(eq(TENANT_ID), any(), eq(CURRENCY)))
        .thenReturn(dpo(null, "INSUFFICIENT_PURCHASE_WINDOW"));

    WorkingCapitalResponse res = service.getWorkingCapital(null);

    assertThat(res.operatingCashGapDays()).isNull();
    assertThat(res.dioPending()).isTrue();
    assertThat(res.cccComplete()).isFalse();
    assertThat(res.warnings())
        .extracting(WorkingCapitalWarningDto::code)
        .containsExactlyInAnyOrder("INSUFFICIENT_SALES_WINDOW", "INSUFFICIENT_PURCHASE_WINDOW");
  }
}
