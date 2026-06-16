package com.fabricmanagement.finance.receivables.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.app.InvoiceSideResolver;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoicePaymentStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import com.fabricmanagement.finance.payment.infra.repository.PaymentAllocationRepository;
import com.fabricmanagement.finance.receivables.dto.ReceivablesCustomerDto;
import com.fabricmanagement.finance.receivables.dto.ReceivablesSummaryDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ReceivablesInsightServiceTest {

  private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 6, 16);

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private PaymentAllocationRepository paymentAllocationRepository;
  @Mock private TenantReportingCurrencyPort reportingCurrencyPort;
  @Mock private ExchangeRateService exchangeRateService;
  @Mock private InvoiceSideResolver invoiceSideResolver;
  @Mock private TradingPartnerResolver tradingPartnerResolver;

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-16T12:00:00Z"), ZoneOffset.UTC);
  private final UUID tenantId = UUID.randomUUID();
  private final UUID partnerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    when(reportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void creditNotesAreContraForOutstandingButExcludedFromAgingAndOverdue() {
    Invoice sales =
        invoice(
            InvoiceType.SALES,
            InvoiceStatus.SENT,
            InvoicePaymentStatus.UNPAID,
            "GBP",
            "1000.00",
            AS_OF_DATE.minusDays(100));
    Invoice creditNote =
        invoice(
            InvoiceType.CREDIT_NOTE,
            InvoiceStatus.SENT,
            InvoicePaymentStatus.UNPAID,
            "GBP",
            "200.00",
            AS_OF_DATE.minusDays(120));

    stubOpenAr(List.of(sales, creditNote));
    when(invoiceSideResolver.resolveSide(tenantId, creditNote))
        .thenReturn(InvoiceSide.ACCOUNTS_RECEIVABLE);
    when(invoiceRepository.findIssuedInvoicesInWindow(any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    ReceivablesSummaryDto summary = service().getSummary(5);

    assertThat(summary.totalOutstanding()).isEqualByComparingTo("800.0000");
    assertThat(summary.overdueExposure()).isEqualByComparingTo("1000.0000");
    assertThat(bucket(summary, "DAYS_90_PLUS")).isEqualByComparingTo("1000.0000");
    assertThat(summary.perCurrencyBreakdown().get(0).documentAmount())
        .isEqualByComparingTo("800.0000");
  }

  @Test
  void customerRollupUsesRemainingCreditOnlyOnceAndSlowPayerIgnoresEarlyPayment() {
    Invoice sales =
        invoice(
            InvoiceType.SALES,
            InvoiceStatus.SENT,
            InvoicePaymentStatus.PARTIALLY_PAID,
            "GBP",
            "700.00",
            AS_OF_DATE.minusDays(20));
    Invoice remainingCredit =
        invoice(
            InvoiceType.CREDIT_NOTE,
            InvoiceStatus.SENT,
            InvoicePaymentStatus.PARTIALLY_PAID,
            "GBP",
            "50.00",
            AS_OF_DATE.minusDays(45));

    stubOpenAr(List.of(sales, remainingCredit));
    when(invoiceSideResolver.resolveSide(tenantId, remainingCredit))
        .thenReturn(InvoiceSide.ACCOUNTS_RECEIVABLE);
    when(tradingPartnerResolver.resolveDisplayNames(tenantId, List.of(partnerId)))
        .thenReturn(Map.of(partnerId, "Acme Textiles"));
    when(paymentAllocationRepository.findPaymentTimingRows(
            tenantId,
            PaymentDirection.INBOUND,
            PaymentStatus.VOIDED,
            AS_OF_DATE.minusDays(365),
            AS_OF_DATE))
        .thenReturn(
            List.of(
                new Object[] {partnerId, AS_OF_DATE.minusDays(30), AS_OF_DATE.minusDays(35)},
                new Object[] {partnerId, AS_OF_DATE.minusDays(30), AS_OF_DATE.minusDays(10)}));

    Page<ReceivablesCustomerDto> page = service().getCustomers(PageRequest.of(0, 20));

    ReceivablesCustomerDto customer = page.getContent().get(0);
    assertThat(customer.outstanding()).isEqualByComparingTo("650.0000");
    assertThat(customer.unappliedCredits()).isEqualByComparingTo("50.0000");
    assertThat(customer.averageDaysLate()).isEqualByComparingTo("10.0000");
    assertThat(customer.riskFlags()).extracting("code").doesNotContain("SLOW_PAYER");
  }

  @Test
  void multiCurrencyCreditNoteConvertsMagnitudeBeforeApplyingContraSign() {
    Invoice creditNote =
        invoice(
            InvoiceType.CREDIT_NOTE,
            InvoiceStatus.SENT,
            InvoicePaymentStatus.UNPAID,
            "USD",
            "100.00",
            AS_OF_DATE.minusDays(30));

    stubOpenAr(List.of(creditNote));
    when(invoiceSideResolver.resolveSide(tenantId, creditNote))
        .thenReturn(InvoiceSide.ACCOUNTS_RECEIVABLE);
    when(exchangeRateService.convert(tenantId, new BigDecimal("100.00"), "USD", "GBP", AS_OF_DATE))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("100.00"),
                "USD",
                new BigDecimal("80.0000"),
                "GBP",
                new BigDecimal("0.800000"),
                AS_OF_DATE));
    when(invoiceRepository.findIssuedInvoicesInWindow(any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    ReceivablesSummaryDto summary = service().getSummary(5);

    assertThat(summary.totalOutstanding()).isEqualByComparingTo("-80.0000");
    verify(exchangeRateService)
        .convert(tenantId, new BigDecimal("100.00"), "USD", "GBP", AS_OF_DATE);
  }

  @Test
  void missingRateFallsBackToIssueRateAndWarnsWithoutFailingRead() {
    Invoice sales =
        invoice(
            InvoiceType.SALES,
            InvoiceStatus.SENT,
            InvoicePaymentStatus.UNPAID,
            "EUR",
            "100.00",
            AS_OF_DATE.plusDays(10));
    sales.captureReportingSnapshot(
        "GBP", new BigDecimal("0.900000"), AS_OF_DATE.minusDays(2), new BigDecimal("90.0000"));

    stubOpenAr(List.of(sales));
    when(exchangeRateService.convert(tenantId, new BigDecimal("100.00"), "EUR", "GBP", AS_OF_DATE))
        .thenThrow(new ExchangeRateRequiredException("EUR", "GBP", AS_OF_DATE));
    when(invoiceRepository.findIssuedInvoicesInWindow(any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    ReceivablesSummaryDto summary = service().getSummary(5);

    assertThat(summary.totalOutstanding()).isEqualByComparingTo("90.0000");
    assertThat(summary.warnings()).extracting("code").contains("ISSUE_RATE_FALLBACK");
  }

  @Test
  void missingRateWithoutIssueRateWarnsAndKeepsReadDegraded() {
    Invoice sales =
        invoice(
            InvoiceType.SALES,
            InvoiceStatus.SENT,
            InvoicePaymentStatus.UNPAID,
            "EUR",
            "100.00",
            AS_OF_DATE.plusDays(10));

    stubOpenAr(List.of(sales));
    when(exchangeRateService.convert(tenantId, new BigDecimal("100.00"), "EUR", "GBP", AS_OF_DATE))
        .thenThrow(new ExchangeRateRequiredException("EUR", "GBP", AS_OF_DATE));
    when(invoiceRepository.findIssuedInvoicesInWindow(any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    ReceivablesSummaryDto summary = service().getSummary(5);

    assertThat(summary.totalOutstanding()).isEqualByComparingTo("100.0000");
    assertThat(summary.warnings()).extracting("code").contains("MISSING_RATE");
  }

  private BigDecimal bucket(ReceivablesSummaryDto summary, String bucket) {
    return summary.agingBuckets().stream()
        .filter(dto -> dto.bucket().equals(bucket))
        .findFirst()
        .orElseThrow()
        .amount();
  }

  private void stubOpenAr(List<Invoice> invoices) {
    when(invoiceRepository.findOpenAccountsReceivable(
            tenantId,
            List.of(InvoiceType.SALES, InvoiceType.DEBIT_NOTE),
            InvoiceType.CREDIT_NOTE,
            List.of(InvoiceStatus.CANCELLED, InvoiceStatus.VOIDED, InvoiceStatus.DRAFT)))
        .thenReturn(invoices);
  }

  private ReceivablesInsightService service() {
    return new ReceivablesInsightService(
        invoiceRepository,
        paymentAllocationRepository,
        reportingCurrencyPort,
        exchangeRateService,
        invoiceSideResolver,
        tradingPartnerResolver,
        clock);
  }

  private Invoice invoice(
      InvoiceType type,
      InvoiceStatus status,
      InvoicePaymentStatus paymentStatus,
      String currency,
      String amount,
      LocalDate dueDate) {
    Invoice invoice =
        Invoice.builder()
            .tradingPartnerId(partnerId)
            .invoiceType(type)
            .status(status)
            .paymentStatus(paymentStatus)
            .issueDate(AS_OF_DATE.minusDays(40))
            .dueDate(dueDate)
            .subtotal(Money.of(new BigDecimal(amount), currency))
            .totalAmount(Money.of(new BigDecimal(amount), currency))
            .amountPaid(Money.zero(currency))
            .amountCredited(Money.zero(currency))
            .amountDue(Money.of(new BigDecimal(amount), currency))
            .build();
    invoice.setId(UUID.randomUUID());
    invoice.setTenantId(tenantId);
    return invoice;
  }
}
