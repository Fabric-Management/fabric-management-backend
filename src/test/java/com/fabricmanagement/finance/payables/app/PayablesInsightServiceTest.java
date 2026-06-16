package com.fabricmanagement.finance.payables.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.app.InvoiceSideResolver;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoicePaymentStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.payables.dto.PayablesSummaryDto;
import com.fabricmanagement.finance.payables.dto.PayablesSupplierDto;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import com.fabricmanagement.finance.payment.infra.repository.PaymentAllocationRepository;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
class PayablesInsightServiceTest {

  private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 6, 16);

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private PaymentAllocationRepository paymentAllocationRepository;
  @Mock private TenantReportingCurrencyPort reportingCurrencyPort;
  @Mock private ExchangeRateService exchangeRateService;
  @Mock private InvoiceSideResolver invoiceSideResolver;
  @Mock private TradingPartnerResolver tradingPartnerResolver;
  private Clock clock = Clock.fixed(Instant.parse("2026-06-16T12:00:00Z"), ZoneId.of("UTC"));

  private final UUID tenantId = UUID.randomUUID();
  private final UUID partnerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    lenient().when(reportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void creditNotesAreContraForOutstandingButExcludedFromAgingAndOverdue() {
    Invoice purchase =
        invoice(
            InvoiceType.PURCHASE,
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

    stubOpenAp(List.of(purchase, creditNote));
    lenient()
        .when(invoiceSideResolver.resolveSide(tenantId, purchase))
        .thenReturn(InvoiceSide.ACCOUNTS_PAYABLE);
    lenient()
        .when(invoiceSideResolver.resolveSide(tenantId, creditNote))
        .thenReturn(InvoiceSide.ACCOUNTS_PAYABLE);
    lenient()
        .when(invoiceRepository.findIssuedInvoicesInWindow(any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    PayablesSummaryDto summary = service().getSummary(5);

    assertThat(summary.totalOutstanding()).isEqualByComparingTo("800.0000");
    assertThat(summary.overdueExposure()).isEqualByComparingTo("1000.0000");
    assertThat(bucket(summary, "DAYS_90_PLUS")).isEqualByComparingTo("1000.0000");
    assertThat(summary.perCurrencyBreakdown().get(0).documentAmount())
        .isEqualByComparingTo("800.0000");
  }

  @Test
  void supplierRollupUsesRemainingCreditOnlyOnceAndChecksOutboundPayments() {
    Invoice purchase =
        invoice(
            InvoiceType.PURCHASE,
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

    stubOpenAp(List.of(purchase, remainingCredit));
    lenient()
        .when(invoiceSideResolver.resolveSide(tenantId, purchase))
        .thenReturn(InvoiceSide.ACCOUNTS_PAYABLE);
    lenient()
        .when(invoiceSideResolver.resolveSide(tenantId, remainingCredit))
        .thenReturn(InvoiceSide.ACCOUNTS_PAYABLE);
    lenient()
        .when(tradingPartnerResolver.resolveDisplayNames(tenantId, List.of(partnerId)))
        .thenReturn(Map.of(partnerId, "Acme Supplier"));
    lenient()
        .when(
            paymentAllocationRepository.findPaymentTimingRows(
                tenantId,
                PaymentDirection.OUTBOUND,
                PaymentStatus.VOIDED,
                AS_OF_DATE.minusDays(365),
                AS_OF_DATE))
        .thenReturn(
            List.of(
                new Object[] {partnerId, AS_OF_DATE.minusDays(30), AS_OF_DATE.minusDays(35)},
                new Object[] {partnerId, AS_OF_DATE.minusDays(30), AS_OF_DATE.minusDays(10)}));

    Page<PayablesSupplierDto> page = service().getSuppliers(PageRequest.of(0, 20));

    PayablesSupplierDto supplier = page.getContent().get(0);
    assertThat(supplier.outstanding()).isEqualByComparingTo("650.0000");
    assertThat(supplier.unappliedCredits()).isEqualByComparingTo("50.0000");
    assertThat(supplier.averageDaysLate()).isEqualByComparingTo("10.0000");
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

    stubOpenAp(List.of(creditNote));
    lenient()
        .when(invoiceSideResolver.resolveSide(tenantId, creditNote))
        .thenReturn(InvoiceSide.ACCOUNTS_PAYABLE);
    lenient()
        .when(
            exchangeRateService.convert(
                tenantId, new BigDecimal("100.00"), "USD", "GBP", AS_OF_DATE))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("100.00"),
                "USD",
                new BigDecimal("80.0000"),
                "GBP",
                new BigDecimal("0.800000"),
                AS_OF_DATE));
    lenient()
        .when(invoiceRepository.findIssuedInvoicesInWindow(any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    PayablesSummaryDto summary = service().getSummary(5);

    assertThat(summary.totalOutstanding()).isEqualByComparingTo("-80.0000");
    verify(exchangeRateService)
        .convert(tenantId, new BigDecimal("100.00"), "USD", "GBP", AS_OF_DATE);
  }

  @Test
  void largeUpcomingOutflowRiskFlagFiresCorrectly() {
    Invoice purchase =
        invoice(
            InvoiceType.PURCHASE,
            InvoiceStatus.SENT,
            InvoicePaymentStatus.UNPAID,
            "GBP",
            "1000.00",
            AS_OF_DATE.plusDays(3)); // Due in 3 days

    stubOpenAp(List.of(purchase));
    lenient()
        .when(invoiceSideResolver.resolveSide(tenantId, purchase))
        .thenReturn(InvoiceSide.ACCOUNTS_PAYABLE);
    lenient()
        .when(invoiceRepository.findIssuedInvoicesInWindow(any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    PayablesSummaryDto summary = service().getSummary(5);

    // It's 100% of AP, so LARGE_UPCOMING_OUTFLOW should fire.
    assertThat(summary.concentration().get(0).outstanding()).isEqualByComparingTo("1000.0000");

    Page<PayablesSupplierDto> page = service().getSuppliers(PageRequest.of(0, 20));
    PayablesSupplierDto supplier = page.getContent().get(0);
    assertThat(supplier.riskFlags()).extracting("code").contains("LARGE_UPCOMING_OUTFLOW");
  }

  private BigDecimal bucket(PayablesSummaryDto summary, String bucket) {
    return summary.agingBuckets().stream()
        .filter(dto -> dto.bucket().equals(bucket))
        .findFirst()
        .orElseThrow()
        .amount();
  }

  private void stubOpenAp(List<Invoice> invoices) {
    lenient()
        .when(
            invoiceRepository.findOpenAccountsPayable(
                tenantId,
                List.of(InvoiceType.PURCHASE),
                InvoiceType.CREDIT_NOTE,
                List.of(InvoiceStatus.CANCELLED, InvoiceStatus.VOIDED, InvoiceStatus.DRAFT)))
        .thenReturn(invoices);
  }

  private PayablesInsightService service() {
    return new PayablesInsightService(
        invoiceRepository,
        paymentAllocationRepository,
        reportingCurrencyPort,
        new OpenInvoiceAmountService(exchangeRateService, invoiceSideResolver),
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
