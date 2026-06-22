package com.fabricmanagement.finance.common.app.port;

import com.fabricmanagement.finance.common.app.port.dto.AnalyticsRevenueResponse;
import com.fabricmanagement.finance.payables.dto.DpoDto;
import com.fabricmanagement.finance.receivables.dto.DsoDto;
import java.time.LocalDate;
import java.util.UUID;

public interface AnalyticsFinancePort {
  /**
   * Retrieves issued revenue within a window, per customer, converted to the reporting currency.
   * Excludes DRAFT, CANCELLED, and VOIDED invoices. Applies FX conversion to the total amount and
   * captures any warnings for missing rates.
   */
  AnalyticsRevenueResponse getIssuedRevenueByCustomer(
      UUID tenantId, LocalDate fromDate, LocalDate toDate, String reportingCurrency);

  /**
   * Computes Days Sales Outstanding (trailing-90 window) for the given tenant. Delegates to
   * ReceivablesInsightService — no formula duplication. Returns null DSO value when there is
   * insufficient sales window data.
   */
  DsoDto getDso(UUID tenantId, LocalDate asOfDate, String reportingCurrency);

  /**
   * Computes Days Payable Outstanding (trailing-90 window) for the given tenant. Delegates to
   * PayablesInsightService — no formula duplication. Returns null DPO value when there is
   * insufficient purchase window data.
   */
  DpoDto getDpo(UUID tenantId, LocalDate asOfDate, String reportingCurrency);
}
