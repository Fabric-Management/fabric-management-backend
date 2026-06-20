package com.fabricmanagement.finance.common.app.port;

import com.fabricmanagement.finance.common.app.port.dto.AnalyticsRevenueResponse;
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
}
