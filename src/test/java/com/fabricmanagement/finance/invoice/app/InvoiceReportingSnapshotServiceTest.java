package com.fabricmanagement.finance.invoice.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceReportingSnapshotServiceTest {

  @Mock private TenantReportingCurrencyPort reportingCurrencyPort;
  @Mock private ExchangeRateService exchangeRateService;
  @Mock private InvoiceRepository invoiceRepository;

  @Test
  void captureAtIssue_foreignCurrencyStoresReportingSnapshot() {
    UUID tenantId = UUID.randomUUID();
    LocalDate issueDate = LocalDate.of(2026, 6, 1);
    Invoice invoice = invoice("USD", "1000.00", issueDate);

    when(reportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");
    when(exchangeRateService.convert(tenantId, new BigDecimal("1000.00"), "USD", "GBP", issueDate))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("1000.00"),
                "USD",
                new BigDecimal("800.0000"),
                "GBP",
                new BigDecimal("0.800000"),
                issueDate));

    service().captureAtIssue(tenantId, invoice);

    assertThat(invoice.getReportingCurrency()).isEqualTo("GBP");
    assertThat(invoice.getIssueExchangeRate()).isEqualByComparingTo("0.800000");
    assertThat(invoice.getIssueExchangeRateDate()).isEqualTo(issueDate);
    assertThat(invoice.getReportingTotal()).isEqualByComparingTo("800.0000");
  }

  @Test
  void captureAtIssue_sameCurrencyUsesRateOneWithoutProvider() {
    UUID tenantId = UUID.randomUUID();
    LocalDate issueDate = LocalDate.of(2026, 6, 1);
    Invoice invoice = invoice("GBP", "1000.00", issueDate);

    when(reportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");

    service().captureAtIssue(tenantId, invoice);

    assertThat(invoice.getReportingCurrency()).isEqualTo("GBP");
    assertThat(invoice.getIssueExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(invoice.getIssueExchangeRateDate()).isEqualTo(issueDate);
    assertThat(invoice.getReportingTotal()).isEqualByComparingTo("1000.0000");
    verify(exchangeRateService, never())
        .convert(tenantId, new BigDecimal("1000.00"), "GBP", "GBP", issueDate);
  }

  private InvoiceReportingSnapshotService service() {
    return new InvoiceReportingSnapshotService(
        reportingCurrencyPort, exchangeRateService, invoiceRepository);
  }

  private Invoice invoice(String currency, String amount, LocalDate issueDate) {
    return Invoice.builder()
        .invoiceType(InvoiceType.SALES)
        .issueDate(issueDate)
        .subtotal(Money.of(new BigDecimal(amount), currency))
        .totalAmount(Money.of(new BigDecimal(amount), currency))
        .amountPaid(Money.zero(currency))
        .amountCredited(Money.zero(currency))
        .amountDue(Money.of(new BigDecimal(amount), currency))
        .build();
  }
}
