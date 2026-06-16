package com.fabricmanagement.finance.cashflow.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.cashflow.dto.CashFlowBucketDto;
import com.fabricmanagement.finance.cashflow.dto.CashFlowForecastDto;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService.OpenAmountResult;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoicePaymentStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CashFlowForecastServiceTest {

  private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 6, 16);

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private TenantReportingCurrencyPort reportingCurrencyPort;
  @Mock private OpenInvoiceAmountService openInvoiceAmountService;

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-16T12:00:00Z"), ZoneOffset.UTC);
  private final UUID tenantId = UUID.randomUUID();

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
  void generatesFlatZeroBucketsForEmptyTenant() {
    stubAr(List.of());
    stubAp(List.of());

    CashFlowForecastDto forecast = service().generateForecast(null, 13);

    assertThat(forecast.asOfDate()).isEqualTo(AS_OF_DATE);
    assertThat(forecast.reportingCurrency()).isEqualTo("GBP");
    assertThat(forecast.mode()).isEqualTo("NET_MOVEMENT");
    assertThat(forecast.buckets()).hasSize(14); // 1 overdue + 13 weeks
    assertThat(forecast.buckets().get(0).inflows()).isEqualByComparingTo("0");
    assertThat(forecast.buckets().get(0).outflows()).isEqualByComparingTo("0");
    assertThat(forecast.buckets().get(0).netMovement()).isEqualByComparingTo("0");
    assertThat(forecast.hasCrunch()).isFalse();
  }

  @Test
  void pastDueAndDueTodayLandInOverdueNowBucket() {
    Invoice pastDueAr = invoice(InvoiceType.SALES, "100.00", AS_OF_DATE.minusDays(10));
    Invoice dueTodayAp = invoice(InvoiceType.PURCHASE, "50.00", AS_OF_DATE);

    stubAr(List.of(pastDueAr));
    stubAp(List.of(dueTodayAp));
    stubAmount(pastDueAr, InvoiceSide.ACCOUNTS_RECEIVABLE, "100.00");
    stubAmount(dueTodayAp, InvoiceSide.ACCOUNTS_PAYABLE, "50.00");

    CashFlowForecastDto forecast = service().generateForecast(null, 13);

    CashFlowBucketDto overdueBucket = forecast.buckets().get(0);
    assertThat(overdueBucket.bucketKey()).isEqualTo("OVERDUE_NOW");
    assertThat(overdueBucket.inflows()).isEqualByComparingTo("100.00");
    assertThat(overdueBucket.outflows()).isEqualByComparingTo("50.00");
    assertThat(overdueBucket.netMovement()).isEqualByComparingTo("50.00");
  }

  @Test
  void openingBalanceProjectionAndFirstNegativeBucket() {
    Invoice futureAr = invoice(InvoiceType.SALES, "1000.00", AS_OF_DATE.plusDays(10)); // Week 2
    Invoice hugeAp = invoice(InvoiceType.PURCHASE, "5000.00", AS_OF_DATE.plusDays(15)); // Week 3

    stubAr(List.of(futureAr));
    stubAp(List.of(hugeAp));
    stubAmount(futureAr, InvoiceSide.ACCOUNTS_RECEIVABLE, "1000.00");
    stubAmount(hugeAp, InvoiceSide.ACCOUNTS_PAYABLE, "5000.00");

    CashFlowForecastDto forecast = service().generateForecast(new BigDecimal("3000.00"), 13);

    assertThat(forecast.mode()).isEqualTo("PROJECTED_POSITION");
    assertThat(forecast.openingBalance()).isEqualByComparingTo("3000.00");
    assertThat(forecast.hasCrunch()).isTrue();
    assertThat(forecast.firstCrunchBucketKey()).isEqualTo("WEEK_3");

    // Overdue/Now (0 inflows, 0 outflows) -> pos 3000
    assertThat(forecast.buckets().get(0).projectedPosition()).isEqualByComparingTo("3000.00");
    assertThat(forecast.buckets().get(0).cashCrunch()).isFalse();

    // Week 1 (0, 0) -> pos 3000
    assertThat(forecast.buckets().get(1).projectedPosition()).isEqualByComparingTo("3000.00");

    // Week 2 (+1000, 0) -> pos 4000
    assertThat(forecast.buckets().get(2).inflows()).isEqualByComparingTo("1000.00");
    assertThat(forecast.buckets().get(2).projectedPosition()).isEqualByComparingTo("4000.00");

    // Week 3 (0, -5000) -> pos -1000
    assertThat(forecast.buckets().get(3).outflows()).isEqualByComparingTo("5000.00");
    assertThat(forecast.buckets().get(3).projectedPosition()).isEqualByComparingTo("-1000.00");
    assertThat(forecast.buckets().get(3).cashCrunch()).isTrue();
    assertThat(forecast.buckets().get(3).firstCrunch()).isTrue();
  }

  @Test
  void netMovementModeOmitsProjectedPositionAndCrunch() {
    Invoice futureAr = invoice(InvoiceType.SALES, "1000.00", AS_OF_DATE.plusDays(10)); // Week 2
    Invoice hugeAp = invoice(InvoiceType.PURCHASE, "5000.00", AS_OF_DATE.plusDays(15)); // Week 3

    stubAr(List.of(futureAr));
    stubAp(List.of(hugeAp));
    stubAmount(futureAr, InvoiceSide.ACCOUNTS_RECEIVABLE, "1000.00");
    stubAmount(hugeAp, InvoiceSide.ACCOUNTS_PAYABLE, "5000.00");

    CashFlowForecastDto forecast = service().generateForecast(null, 13);

    assertThat(forecast.mode()).isEqualTo("NET_MOVEMENT");
    assertThat(forecast.openingBalance()).isNull();
    assertThat(forecast.hasCrunch()).isFalse();
    assertThat(forecast.firstCrunchBucketKey()).isNull();

    assertThat(forecast.buckets().get(2).inflows()).isEqualByComparingTo("1000.00");
    assertThat(forecast.buckets().get(2).netMovement()).isEqualByComparingTo("1000.00");
    assertThat(forecast.buckets().get(2).projectedPosition()).isNull();

    assertThat(forecast.buckets().get(3).outflows()).isEqualByComparingTo("5000.00");
    assertThat(forecast.buckets().get(3).netMovement()).isEqualByComparingTo("-5000.00");
    assertThat(forecast.buckets().get(3).projectedPosition()).isNull();
    assertThat(forecast.buckets().get(3).cashCrunch()).isFalse();
  }

  @Test
  void apOutflowAndApCreditNoteContraCashFlowLevel() {
    Invoice apPurchase = invoice(InvoiceType.PURCHASE, "1000.00", AS_OF_DATE.plusDays(5)); // Week 1
    Invoice apCreditNote =
        invoice(InvoiceType.CREDIT_NOTE, "200.00", AS_OF_DATE.plusDays(6)); // Week 1

    stubAr(List.of());
    stubAp(List.of(apPurchase, apCreditNote));
    stubAmount(apPurchase, InvoiceSide.ACCOUNTS_PAYABLE, "1000.00");
    stubAmount(apCreditNote, InvoiceSide.ACCOUNTS_PAYABLE, "-200.00");

    CashFlowForecastDto forecast = service().generateForecast(null, 13);

    CashFlowBucketDto week1 = forecast.buckets().get(1); // Week 1
    assertThat(week1.bucketKey()).isEqualTo("WEEK_1");
    assertThat(week1.inflows()).isEqualByComparingTo("0.00");
    assertThat(week1.outflows()).isEqualByComparingTo("800.00");
    assertThat(week1.netMovement()).isEqualByComparingTo("-800.00");
  }

  private void stubAr(List<Invoice> invoices) {
    when(invoiceRepository.findOpenAccountsReceivable(eq(tenantId), any(), any(), any()))
        .thenReturn(invoices);
  }

  private void stubAp(List<Invoice> invoices) {
    when(invoiceRepository.findOpenAccountsPayable(eq(tenantId), any(), any(), any()))
        .thenReturn(invoices);
  }

  private void stubAmount(Invoice invoice, InvoiceSide side, String amount) {
    when(openInvoiceAmountService.signedOpenAmountForSide(
            tenantId, invoice, side, "GBP", AS_OF_DATE))
        .thenReturn(
            new OpenAmountResult(new BigDecimal(amount), new BigDecimal(amount), List.of()));
  }

  private CashFlowForecastService service() {
    return new CashFlowForecastService(
        invoiceRepository, reportingCurrencyPort, openInvoiceAmountService, clock);
  }

  private Invoice invoice(InvoiceType type, String amount, LocalDate dueDate) {
    Invoice invoice =
        Invoice.builder()
            .tradingPartnerId(UUID.randomUUID())
            .invoiceType(type)
            .status(InvoiceStatus.SENT)
            .paymentStatus(InvoicePaymentStatus.UNPAID)
            .issueDate(AS_OF_DATE.minusDays(40))
            .dueDate(dueDate)
            .subtotal(Money.of(new BigDecimal(amount), "GBP"))
            .totalAmount(Money.of(new BigDecimal(amount), "GBP"))
            .amountPaid(Money.zero("GBP"))
            .amountCredited(Money.zero("GBP"))
            .amountDue(Money.of(new BigDecimal(amount), "GBP"))
            .build();
    invoice.setId(UUID.randomUUID());
    invoice.setTenantId(tenantId);
    return invoice;
  }
}
