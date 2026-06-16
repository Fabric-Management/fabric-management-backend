package com.fabricmanagement.finance.invoice.app;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvoiceReportingSnapshotService {

  private static final int REPORTING_AMOUNT_SCALE = 4;

  private final TenantReportingCurrencyPort reportingCurrencyPort;
  private final ExchangeRateService exchangeRateService;
  private final InvoiceRepository invoiceRepository;

  public void captureAtIssue(UUID tenantId, Invoice invoice) {
    capture(tenantId, invoice);
  }

  public void ensureSnapshot(UUID tenantId, Invoice invoice) {
    if (hasSnapshot(invoice)) {
      return;
    }
    capture(tenantId, invoice);
    invoiceRepository.save(invoice);
  }

  private void capture(UUID tenantId, Invoice invoice) {
    String reportingCurrency = reportingCurrencyPort.getReportingCurrency(tenantId);
    String documentCurrency = invoice.getCurrency();
    BigDecimal documentTotal = invoice.getTotalAmount().getAmount();

    if (documentCurrency.equalsIgnoreCase(reportingCurrency)) {
      invoice.captureReportingSnapshot(
          reportingCurrency,
          BigDecimal.ONE,
          invoice.getIssueDate(),
          documentTotal.setScale(REPORTING_AMOUNT_SCALE, RoundingMode.HALF_UP));
      return;
    }

    ConvertedMoney converted =
        exchangeRateService.convert(
            tenantId, documentTotal, documentCurrency, reportingCurrency, invoice.getIssueDate());

    invoice.captureReportingSnapshot(
        reportingCurrency,
        converted.getExchangeRate(),
        converted.getRateDate(),
        converted.getConvertedAmount().setScale(REPORTING_AMOUNT_SCALE, RoundingMode.HALF_UP));
  }

  private boolean hasSnapshot(Invoice invoice) {
    return invoice.getReportingCurrency() != null
        && invoice.getIssueExchangeRate() != null
        && invoice.getIssueExchangeRateDate() != null
        && invoice.getReportingTotal() != null;
  }
}
