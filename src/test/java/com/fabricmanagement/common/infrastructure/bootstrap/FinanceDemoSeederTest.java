package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateSource;
import com.fabricmanagement.finance.invoice.app.InvoiceService;
import com.fabricmanagement.finance.invoice.dto.CreateInvoiceRequest;
import com.fabricmanagement.finance.invoice.dto.InvoiceDto;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinanceDemoSeederTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private OrganizationRepository organizationRepository;
  @Mock private ExchangeRateService exchangeRateService;
  @Mock private TradingPartnerService tradingPartnerService;
  @Mock private InvoiceService invoiceService;
  @Mock private PaymentService paymentService;
  @Mock private CacheManager cacheManager;

  private FinanceDemoSeeder seeder;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-06-21T10:00:00Z"), ZoneId.of("UTC"));
    seeder =
        new FinanceDemoSeeder(
            organizationRepository,
            exchangeRateService,
            tradingPartnerService,
            invoiceService,
            paymentService,
            cacheManager,
            clock);
  }

  private InvoiceDto inv() {
    UUID id = UUID.randomUUID();
    return new InvoiceDto(
        id,
        null,
        null,
        null,
        null,
        null,
        null,
        "SALES",
        "SENT",
        "UNPAID",
        null,
        null,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "USD",
        "USD",
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        0L,
        false);
  }

  @Test
  void seedsReportingCurrencyFxInvoicesAndPayments() {
    // Idempotency anchor absent → proceed.
    when(tradingPartnerService.searchByName(eq(TENANT_ID), any())).thenReturn(List.of());
    when(tradingPartnerService.createPartner(any(CreateTradingPartnerRequest.class)))
        .thenAnswer(i -> TradingPartnerDto.builder().id(UUID.randomUUID()).build());

    Organization rootOrg = org.mockito.Mockito.mock(Organization.class);
    when(organizationRepository.findRootOrganization(TENANT_ID, OrganizationType.EXTERNAL_PARTNER))
        .thenReturn(Optional.of(rootOrg));
    when(cacheManager.getCache("tenantReportingCurrency")).thenReturn(null);

    when(invoiceService.createInvoice(any(CreateInvoiceRequest.class))).thenAnswer(i -> inv());
    when(invoiceService.issueInvoice(any())).thenAnswer(i -> inv());
    when(invoiceService.sendInvoice(any())).thenAnswer(i -> inv());

    seeder.seedFor(TENANT_ID);

    // Reporting currency set to USD on the root org.
    verify(rootOrg).setReportingCurrency("USD");
    verify(organizationRepository).save(rootOrg);

    // FX rates: 2 pairs (USD↔TRY, USD↔EUR) for each of the 11 offsets.
    verify(exchangeRateService, times(11))
        .saveRate(eq("USD"), eq("TRY"), any(), any(), eq(ExchangeRateSource.MANUAL));
    verify(exchangeRateService, times(11))
        .saveRate(eq("USD"), eq("EUR"), any(), any(), eq(ExchangeRateSource.MANUAL));

    // 4 SALES + 3 PURCHASE invoices, each created + issued + sent.
    verify(invoiceService, times(7)).createInvoice(any());
    verify(invoiceService, times(7)).issueInvoice(any());
    verify(invoiceService, times(7)).sendInvoice(any());

    // 3 payments (1 inbound partial, 1 outbound full, 1 outbound partial).
    verify(paymentService, times(3)).createPayment(any(CreatePaymentRequest.class));
  }

  @Test
  void isIdempotentWhenDemoFinanceDataAlreadyExists() {
    when(tradingPartnerService.searchByName(TENANT_ID, FinanceDemoSeeder.CUSTOMER_ANADOLU))
        .thenReturn(List.of(TradingPartnerDto.builder().id(UUID.randomUUID()).build()));

    seeder.seedFor(TENANT_ID);

    verify(invoiceService, never()).createInvoice(any());
    verify(paymentService, never()).createPayment(any());
    verify(exchangeRateService, never()).saveRate(any(), any(), any(), any(), any());
    verify(organizationRepository, never()).save(any());
  }
}
