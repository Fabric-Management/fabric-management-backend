package com.fabricmanagement.finance.fx.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.finance.common.app.SettlementFxResult;
import com.fabricmanagement.finance.fx.domain.FxRealization;
import com.fabricmanagement.finance.fx.domain.FxRealizationSourceType;
import com.fabricmanagement.finance.fx.infra.repository.FxRealizationRepository;
import com.fabricmanagement.finance.invoice.app.InvoiceReportingSnapshotService;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.app.InvoiceSideResolver;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealizedFxServiceTest {

  @Mock private FxRealizationRepository fxRealizationRepository;
  @Mock private InvoiceReportingSnapshotService snapshotService;
  @Mock private InvoiceSideResolver sideResolver;
  @Mock private ExchangeRateService exchangeRateService;
  @Captor private ArgumentCaptor<FxRealization> fxCaptor;

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void recordPaymentAllocation_arUsesPositiveSideSign() {
    SettlementFxResult result = recordWithSide(InvoiceSide.ACCOUNTS_RECEIVABLE);

    assertThat(result.realizedFxGainLoss()).isEqualByComparingTo("-20.0000");
    verify(fxRealizationRepository).save(fxCaptor.capture());
    assertThat(fxCaptor.getValue().getRealizedGainLoss()).isEqualByComparingTo("-20.0000");
  }

  @Test
  void recordPaymentAllocation_apMirrorsTheSign() {
    SettlementFxResult result = recordWithSide(InvoiceSide.ACCOUNTS_PAYABLE);

    assertThat(result.realizedFxGainLoss()).isEqualByComparingTo("20.0000");
  }

  @Test
  void recordPaymentAllocation_sameCurrencyDoesNotPersistFxRow() {
    UUID tenantId = UUID.randomUUID();
    UUID allocationId = UUID.randomUUID();
    Invoice invoice = invoice("GBP", "1000.00");
    invoice.captureReportingSnapshot(
        "GBP", BigDecimal.ONE, LocalDate.of(2026, 6, 1), new BigDecimal("1000.0000"));

    SettlementFxResult result =
        service()
            .recordPaymentAllocation(
                tenantId,
                invoice,
                allocationId,
                Money.of(new BigDecimal("1000.00"), "GBP"),
                LocalDate.of(2026, 6, 15));

    assertThat(result.realizedFxGainLoss()).isEqualByComparingTo(BigDecimal.ZERO);
    verify(exchangeRateService, never()).convert(any(), any(), any(), any(), any());
    verify(fxRealizationRepository, never()).save(any());
  }

  @Test
  void reversePaymentAllocationWritesNegativeReversalRow() {
    UUID tenantId = UUID.randomUUID();
    UUID allocationId = UUID.randomUUID();
    UUID originalFxId = UUID.randomUUID();

    FxRealization original =
        FxRealization.builder()
            .sourceType(FxRealizationSourceType.PAYMENT_ALLOCATION)
            .sourceId(allocationId)
            .invoiceId(UUID.randomUUID())
            .documentAmount(Money.of(new BigDecimal("1000.00"), "USD"))
            .reportingCurrency("GBP")
            .issueExchangeRate(new BigDecimal("0.800000"))
            .issueExchangeRateDate(LocalDate.of(2026, 6, 1))
            .settlementExchangeRate(new BigDecimal("0.780000"))
            .settlementExchangeRateDate(LocalDate.of(2026, 6, 15))
            .realizedGainLoss(new BigDecimal("-20.0000"))
            .realizedAt(Instant.now(clock))
            .build();
    original.setId(originalFxId);
    original.setTenantId(tenantId);

    when(fxRealizationRepository.findByTenantIdAndSourceTypeAndSourceIdAndReversalOfIdIsNull(
            tenantId, FxRealizationSourceType.PAYMENT_ALLOCATION, allocationId))
        .thenReturn(List.of(original));
    when(fxRealizationRepository.existsByTenantIdAndReversalOfId(tenantId, originalFxId))
        .thenReturn(false);

    service().reversePaymentAllocation(tenantId, allocationId);

    verify(fxRealizationRepository).save(fxCaptor.capture());
    FxRealization reversal = fxCaptor.getValue();
    assertThat(reversal.getReversalOfId()).isEqualTo(originalFxId);
    assertThat(reversal.getRealizedGainLoss()).isEqualByComparingTo("20.0000");
  }

  private SettlementFxResult recordWithSide(InvoiceSide side) {
    UUID tenantId = UUID.randomUUID();
    UUID allocationId = UUID.randomUUID();
    LocalDate settlementDate = LocalDate.of(2026, 6, 15);
    Invoice invoice = invoice("USD", "1000.00");
    invoice.captureReportingSnapshot(
        "GBP", new BigDecimal("0.800000"), LocalDate.of(2026, 6, 1), new BigDecimal("800.0000"));

    when(fxRealizationRepository
            .findFirstByTenantIdAndSourceTypeAndSourceIdAndReversalOfIdIsNullOrderByCreatedAtAsc(
                tenantId, FxRealizationSourceType.PAYMENT_ALLOCATION, allocationId))
        .thenReturn(Optional.empty());
    when(exchangeRateService.convert(
            tenantId, new BigDecimal("1000.00"), "USD", "GBP", settlementDate))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("1000.00"),
                "USD",
                new BigDecimal("780.0000"),
                "GBP",
                new BigDecimal("0.780000"),
                settlementDate));
    when(sideResolver.resolveSide(tenantId, invoice)).thenReturn(side);
    when(fxRealizationRepository.save(any(FxRealization.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    return service()
        .recordPaymentAllocation(
            tenantId,
            invoice,
            allocationId,
            Money.of(new BigDecimal("1000.00"), "USD"),
            settlementDate);
  }

  private RealizedFxService service() {
    return new RealizedFxService(
        fxRealizationRepository, snapshotService, sideResolver, exchangeRateService, clock);
  }

  private Invoice invoice(String currency, String amount) {
    Invoice invoice =
        Invoice.builder()
            .invoiceType(InvoiceType.SALES)
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
}
