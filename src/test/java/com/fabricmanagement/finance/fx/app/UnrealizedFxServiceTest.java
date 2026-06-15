package com.fabricmanagement.finance.fx.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.finance.common.exception.FinanceDomainException;
import com.fabricmanagement.finance.fx.domain.FxRevaluation;
import com.fabricmanagement.finance.fx.domain.FxRevaluationEntryType;
import com.fabricmanagement.finance.fx.infra.repository.FxRevaluationRepository;
import com.fabricmanagement.finance.invoice.app.InvoiceReportingSnapshotService;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.app.InvoiceSideResolver;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import com.fabricmanagement.finance.period.domain.FinancialPeriod;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnrealizedFxServiceTest {

  @Mock private FxRevaluationRepository fxRevaluationRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private InvoiceReportingSnapshotService snapshotService;
  @Mock private InvoiceSideResolver sideResolver;
  @Mock private ExchangeRateService exchangeRateService;

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-30T20:00:00Z"), ZoneOffset.UTC);

  @Test
  void closePeriodReversesPreviousRevaluationThenBooksCurrentOpenInvoiceExposure() {
    UUID tenantId = UUID.randomUUID();
    FinancialPeriod previous = period(tenantId, YearMonth.of(2026, 5));
    FinancialPeriod current = period(tenantId, YearMonth.of(2026, 6));
    FxRevaluation previousEntry =
        revaluation(tenantId, previous.getId(), new BigDecimal("50.0000"));
    Invoice invoice = invoice("USD", "1000.00");

    when(fxRevaluationRepository.findUnreversedEntriesForPeriodByType(
            tenantId, previous.getId(), FxRevaluationEntryType.REVALUATION))
        .thenReturn(List.of(previousEntry));
    when(invoiceRepository.findOpenForeignCurrencyForRevaluation(
            tenantId,
            "GBP",
            List.of(InvoiceStatus.ISSUED, InvoiceStatus.SENT, InvoiceStatus.DISPUTED)))
        .thenReturn(List.of(invoice));
    doAnswer(
            invocation -> {
              invoice.captureReportingSnapshot(
                  "GBP",
                  new BigDecimal("0.800000"),
                  LocalDate.of(2026, 6, 1),
                  new BigDecimal("800.0000"));
              return null;
            })
        .when(snapshotService)
        .ensureSnapshot(tenantId, invoice);
    when(exchangeRateService.convert(
            tenantId, new BigDecimal("1000.00"), "USD", "GBP", current.getEndDate()))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("1000.00"),
                "USD",
                new BigDecimal("780.0000"),
                "GBP",
                new BigDecimal("0.780000"),
                LocalDate.of(2026, 6, 28)));
    when(sideResolver.resolveSide(tenantId, invoice)).thenReturn(InvoiceSide.ACCOUNTS_RECEIVABLE);
    when(fxRevaluationRepository.saveAll(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UnrealizedFxService.CloseResult result =
        service().closePeriod(tenantId, current, previous, "GBP");

    assertThat(result.reversedEntryCount()).isEqualTo(1);
    assertThat(result.revaluationEntryCount()).isEqualTo(1);
    assertThat(result.periodMovement()).isEqualByComparingTo("-70.0000");
    assertThat(result.entries()).hasSize(2);
    assertThat(result.entries().get(0).getEntryType()).isEqualTo(FxRevaluationEntryType.REVERSAL);
    assertThat(result.entries().get(0).getUnrealizedGainLoss()).isEqualByComparingTo("-50.0000");
    assertThat(result.entries().get(1).getUnrealizedGainLoss()).isEqualByComparingTo("-20.0000");
    assertThat(result.entries().get(1).getClosingExchangeRateDate())
        .isEqualTo(LocalDate.of(2026, 6, 28));
  }

  @Test
  void closePeriodBlocksAndWritesNothingWhenClosingRateIsMissing() {
    UUID tenantId = UUID.randomUUID();
    FinancialPeriod current = period(tenantId, YearMonth.of(2026, 6));
    Invoice invoice = invoice("USD", "1000.00");

    when(invoiceRepository.findOpenForeignCurrencyForRevaluation(
            tenantId,
            "GBP",
            List.of(InvoiceStatus.ISSUED, InvoiceStatus.SENT, InvoiceStatus.DISPUTED)))
        .thenReturn(List.of(invoice));
    doAnswer(
            invocation -> {
              invoice.captureReportingSnapshot(
                  "GBP",
                  new BigDecimal("0.800000"),
                  LocalDate.of(2026, 6, 1),
                  new BigDecimal("800.0000"));
              return null;
            })
        .when(snapshotService)
        .ensureSnapshot(tenantId, invoice);
    when(exchangeRateService.convert(
            tenantId, new BigDecimal("1000.00"), "USD", "GBP", current.getEndDate()))
        .thenThrow(new ExchangeRateRequiredException("USD", "GBP", current.getEndDate()));

    assertThatThrownBy(() -> service().closePeriod(tenantId, current, null, "GBP"))
        .isInstanceOf(FinanceDomainException.class)
        .hasMessageContaining("missing exchange rates");

    verify(fxRevaluationRepository, never()).saveAll(any());
  }

  @Test
  void standingPositionNetsReversalOfReversalThroughAsOfDate() {
    UUID tenantId = UUID.randomUUID();
    LocalDate mayEnd = LocalDate.of(2026, 5, 31);
    FxRevaluation mayRevaluation =
        revaluation(tenantId, UUID.randomUUID(), new BigDecimal("50.0000"));
    FxRevaluation juneCloseReversal =
        mayRevaluation.reversal(UUID.randomUUID(), Instant.parse("2026-06-30T20:00:00Z"));
    juneCloseReversal.setId(UUID.randomUUID());
    FxRevaluation juneReopenReversal =
        juneCloseReversal.reversal(UUID.randomUUID(), Instant.parse("2026-07-01T09:00:00Z"));
    juneReopenReversal.setId(UUID.randomUUID());

    when(fxRevaluationRepository.findPositionEntriesThroughAsOfDate(tenantId, mayEnd))
        .thenReturn(List.of(mayRevaluation, juneCloseReversal, juneReopenReversal));

    UnrealizedFxService.PositionResult result = service().getStandingPosition(tenantId, mayEnd);

    assertThat(result.position()).isEqualByComparingTo("50.0000");
    assertThat(result.entries())
        .extracting(FxRevaluation::getUnrealizedGainLoss)
        .containsExactly(
            new BigDecimal("50.0000"), new BigDecimal("-50.0000"), new BigDecimal("50.0000"));
  }

  private UnrealizedFxService service() {
    return new UnrealizedFxService(
        fxRevaluationRepository,
        invoiceRepository,
        snapshotService,
        sideResolver,
        exchangeRateService,
        clock);
  }

  private FinancialPeriod period(UUID tenantId, YearMonth month) {
    FinancialPeriod period = FinancialPeriod.forMonth(tenantId, month);
    period.setId(UUID.randomUUID());
    return period;
  }

  private Invoice invoice(String currency, String amount) {
    Invoice invoice =
        Invoice.builder()
            .invoiceType(InvoiceType.SALES)
            .status(InvoiceStatus.ISSUED)
            .issueDate(LocalDate.of(2026, 6, 1))
            .subtotal(Money.of(new BigDecimal(amount), currency))
            .totalAmount(Money.of(new BigDecimal(amount), currency))
            .amountPaid(Money.zero(currency))
            .amountCredited(Money.zero(currency))
            .amountDue(Money.of(new BigDecimal(amount), currency))
            .build();
    invoice.setId(UUID.randomUUID());
    return invoice;
  }

  private FxRevaluation revaluation(UUID tenantId, UUID periodId, BigDecimal gainLoss) {
    FxRevaluation revaluation =
        FxRevaluation.builder()
            .periodId(periodId)
            .invoiceId(UUID.randomUUID())
            .entryType(FxRevaluationEntryType.REVALUATION)
            .invoiceSide(InvoiceSide.ACCOUNTS_RECEIVABLE.name())
            .asOfDate(LocalDate.of(2026, 5, 31))
            .openDocumentAmount(Money.of(new BigDecimal("1000.00"), "USD"))
            .reportingCurrency("GBP")
            .issueExchangeRate(new BigDecimal("0.800000"))
            .issueExchangeRateDate(LocalDate.of(2026, 5, 1))
            .closingExchangeRate(new BigDecimal("0.850000"))
            .closingExchangeRateDate(LocalDate.of(2026, 5, 31))
            .unrealizedGainLoss(gainLoss)
            .revaluedAt(Instant.parse("2026-05-31T20:00:00Z"))
            .build();
    revaluation.setId(UUID.randomUUID());
    revaluation.setTenantId(tenantId);
    return revaluation;
  }
}
