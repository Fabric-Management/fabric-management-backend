package com.fabricmanagement.analytics.app;

import com.fabricmanagement.analytics.dto.WorkingCapitalResponse;
import com.fabricmanagement.analytics.dto.WorkingCapitalWarningDto;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.finance.common.app.port.AnalyticsFinancePort;
import com.fabricmanagement.finance.payables.dto.DpoDto;
import com.fabricmanagement.finance.receivables.dto.DsoDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Working-capital read model composing DSO (INS-1) and DPO (INS-3) via {@link
 * AnalyticsFinancePort}. Reuses the existing finance computations (no formula duplication).
 * Presents only the honest partial "operating cash gap" (DSO − DPO); the full Cash Conversion Cycle
 * and DIO are deferred to INS-7b.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkingCapitalService {

  private final AnalyticsFinancePort analyticsFinancePort;
  private final TenantReportingCurrencyPort reportingCurrencyPort;
  private final Clock clock;

  public WorkingCapitalResponse getWorkingCapital(LocalDate asOfDate) {
    UUID tenantId = TenantContext.requireTenantId();
    LocalDate asOf = asOfDate != null ? asOfDate : LocalDate.now(clock);
    String reportingCurrency = reportingCurrencyPort.getReportingCurrency(tenantId);

    DsoDto dso = analyticsFinancePort.getDso(tenantId, asOf, reportingCurrency);
    DpoDto dpo = analyticsFinancePort.getDpo(tenantId, asOf, reportingCurrency);

    BigDecimal operatingCashGapDays = computeOperatingCashGap(dso, dpo);

    List<WorkingCapitalWarningDto> warnings = new ArrayList<>();
    if (dso != null && dso.status() != null) {
      warnings.add(
          new WorkingCapitalWarningDto(
              dso.status(), "DSO", "Days sales outstanding could not be computed."));
    }
    if (dpo != null && dpo.status() != null) {
      warnings.add(
          new WorkingCapitalWarningDto(
              dpo.status(), "DPO", "Days payable outstanding could not be computed."));
    }

    // dioPending / cccComplete: DIO and the full CCC arrive in INS-7b.
    return new WorkingCapitalResponse(
        dso, dpo, operatingCashGapDays, reportingCurrency, true, false, warnings);
  }

  /** DSO − DPO, in days. Null when either metric is unavailable — never a fabricated value. */
  private BigDecimal computeOperatingCashGap(DsoDto dso, DpoDto dpo) {
    if (dso == null || dpo == null) {
      return null;
    }
    BigDecimal dsoValue = dso.daysSalesOutstanding();
    BigDecimal dpoValue = dpo.daysPayableOutstanding();
    if (dsoValue == null || dpoValue == null) {
      return null;
    }
    return dsoValue.subtract(dpoValue);
  }
}
