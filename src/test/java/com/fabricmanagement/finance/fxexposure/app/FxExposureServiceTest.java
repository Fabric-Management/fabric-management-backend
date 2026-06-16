package com.fabricmanagement.finance.fxexposure.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService;
import com.fabricmanagement.finance.common.app.OpenInvoiceAmountService.OpenAmountResult;
import com.fabricmanagement.finance.fx.domain.FxRealization;
import com.fabricmanagement.finance.fx.domain.FxRevaluation;
import com.fabricmanagement.finance.fx.infra.repository.FxRealizationRepository;
import com.fabricmanagement.finance.fx.infra.repository.FxRevaluationRepository;
import com.fabricmanagement.finance.fxexposure.dto.FxExposureSummaryDto;
import com.fabricmanagement.finance.invoice.app.InvoiceSide;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import com.fabricmanagement.finance.invoice.infra.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FxExposureServiceTest {

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private FxRealizationRepository fxRealizationRepository;
  @Mock private FxRevaluationRepository fxRevaluationRepository;
  @Mock private TenantReportingCurrencyPort reportingCurrencyPort;
  @Mock private OpenInvoiceAmountService openInvoiceAmountService;

  private FxExposureService fxExposureService;
  private Clock clock;
  private UUID tenantId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    clock = Clock.fixed(Instant.parse("2026-06-16T10:00:00Z"), ZoneId.of("UTC"));
    fxExposureService =
        new FxExposureService(
            invoiceRepository,
            fxRealizationRepository,
            fxRevaluationRepository,
            reportingCurrencyPort,
            openInvoiceAmountService,
            clock);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void getSummary_returnsCorrectExposure() {
    when(reportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("USD");

    Invoice eurInvoice = Invoice.builder().subtotal(Money.of(BigDecimal.ZERO, "EUR")).build();

    Invoice usdInvoice = Invoice.builder().subtotal(Money.of(BigDecimal.ZERO, "USD")).build();

    when(invoiceRepository.findOpenAccountsReceivable(
            eq(tenantId), any(), eq(InvoiceType.CREDIT_NOTE), any()))
        .thenReturn(List.of(eurInvoice, usdInvoice));

    when(invoiceRepository.findOpenAccountsPayable(
            eq(tenantId), any(), eq(InvoiceType.CREDIT_NOTE), any()))
        .thenReturn(List.of());

    when(openInvoiceAmountService.signedOpenAmountForSide(
            eq(tenantId), eq(eurInvoice), eq(InvoiceSide.ACCOUNTS_RECEIVABLE), eq("USD"), any()))
        .thenReturn(
            new OpenAmountResult(
                new BigDecimal("100.00"), new BigDecimal("110.00"), new ArrayList<>()));

    when(fxRealizationRepository.findByTenantIdAndRealizedAtBetween(eq(tenantId), any(), any()))
        .thenReturn(List.of());
    when(fxRevaluationRepository.findPositionEntriesThroughAsOfDate(eq(tenantId), any()))
        .thenReturn(List.of());

    FxExposureSummaryDto result = fxExposureService.getSummary();

    assertThat(result.reportingCurrency()).isEqualTo("USD");
    assertThat(result.openExposure()).hasSize(1);
    assertThat(result.openExposure().get(0).currency()).isEqualTo("EUR");
    assertThat(result.openExposure().get(0).grossAccountsReceivable()).isEqualByComparingTo("100");
    assertThat(result.openExposure().get(0).grossAccountsPayable()).isEqualByComparingTo("0");
    assertThat(result.openExposure().get(0).netDocumentCurrency()).isEqualByComparingTo("100");
    assertThat(result.openExposure().get(0).netReportingCurrency()).isEqualByComparingTo("110");
    assertThat(result.totalNetReportingExposure()).isEqualByComparingTo("110");
  }

  @Test
  void getSummary_returnsCorrectRealizedFx() {
    when(reportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("USD");

    when(invoiceRepository.findOpenAccountsReceivable(
            eq(tenantId), any(), eq(InvoiceType.CREDIT_NOTE), any()))
        .thenReturn(List.of());
    when(invoiceRepository.findOpenAccountsPayable(
            eq(tenantId), any(), eq(InvoiceType.CREDIT_NOTE), any()))
        .thenReturn(List.of());

    FxRealization r1 =
        FxRealization.builder()
            .documentAmount(Money.of(new BigDecimal("100"), "EUR"))
            .realizedGainLoss(new BigDecimal("10.50"))
            .build();
    FxRealization r2 =
        FxRealization.builder()
            .documentAmount(Money.of(new BigDecimal("50"), "EUR"))
            .realizedGainLoss(new BigDecimal("-2.50"))
            .build();
    FxRealization rUsd =
        FxRealization.builder() // Should be ignored
            .documentAmount(Money.of(new BigDecimal("100"), "USD"))
            .realizedGainLoss(new BigDecimal("0"))
            .build();

    when(fxRealizationRepository.findByTenantIdAndRealizedAtBetween(eq(tenantId), any(), any()))
        .thenReturn(List.of(r1, r2, rUsd));

    when(fxRevaluationRepository.findPositionEntriesThroughAsOfDate(eq(tenantId), any()))
        .thenReturn(List.of());

    FxExposureSummaryDto result = fxExposureService.getSummary();

    assertThat(result.totalRealizedGainLoss()).isEqualByComparingTo("8.00");
    assertThat(result.realizedFx()).hasSize(1);
    assertThat(result.realizedFx().get(0).currency()).isEqualTo("EUR");
    assertThat(result.realizedFx().get(0).netRealizedGainLoss()).isEqualByComparingTo("8.00");
  }

  @Test
  void getSummary_returnsCorrectUnrealizedFx() {
    when(reportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("USD");

    when(invoiceRepository.findOpenAccountsReceivable(
            eq(tenantId), any(), eq(InvoiceType.CREDIT_NOTE), any()))
        .thenReturn(List.of());
    when(invoiceRepository.findOpenAccountsPayable(
            eq(tenantId), any(), eq(InvoiceType.CREDIT_NOTE), any()))
        .thenReturn(List.of());

    when(fxRealizationRepository.findByTenantIdAndRealizedAtBetween(eq(tenantId), any(), any()))
        .thenReturn(List.of());

    FxRevaluation r1 =
        FxRevaluation.builder()
            .openDocumentAmount(Money.of(new BigDecimal("100"), "GBP"))
            .unrealizedGainLoss(new BigDecimal("-15.00"))
            .asOfDate(LocalDate.parse("2026-05-31"))
            .build();

    when(fxRevaluationRepository.findPositionEntriesThroughAsOfDate(eq(tenantId), any()))
        .thenReturn(List.of(r1));

    FxExposureSummaryDto result = fxExposureService.getSummary();

    assertThat(result.totalUnrealizedGainLoss()).isEqualByComparingTo("-15.00");
    assertThat(result.latestRevaluationDate()).isEqualTo("2026-05-31");
    assertThat(result.unrealizedFx()).hasSize(1);
    assertThat(result.unrealizedFx().get(0).currency()).isEqualTo("GBP");
    assertThat(result.unrealizedFx().get(0).netUnrealizedGainLoss()).isEqualByComparingTo("-15.00");
  }
}
