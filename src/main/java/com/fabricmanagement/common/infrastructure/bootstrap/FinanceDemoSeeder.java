package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateSource;
import com.fabricmanagement.finance.invoice.app.InvoiceService;
import com.fabricmanagement.finance.invoice.dto.CreateInvoiceRequest;
import com.fabricmanagement.finance.invoice.dto.InvoiceDto;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.dto.CreateAllocationRequest;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Seeds a coherent finance dataset (DEMO-1) on top of {@link DemoTransactionSeeder}: sets the demo
 * tenant's reporting currency to USD, seeds USD↔TRY / USD↔EUR exchange rates, and creates a spread
 * of SALES (AR) and PURCHASE (AP) invoices — issued + sent — plus partial/full payments. This makes
 * the real backend compute real numbers for every finance-insights view (DSO/DPO/working-capital,
 * receivables/payables aging, cash-flow, FX-exposure, revenue trends) instead of leaving them
 * empty.
 *
 * <p>Tenant-parametric and idempotent. MUST be invoked with the target tenant already bound in
 * {@code TenantContext} (the calling {@link DemoTransactionSeeder} does this) — the finance/costing
 * services resolve the tenant from context.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinanceDemoSeeder {

  private final OrganizationRepository organizationRepository;
  private final ExchangeRateService exchangeRateService;
  private final TradingPartnerService tradingPartnerService;
  private final InvoiceService invoiceService;
  private final PaymentService paymentService;
  private final CacheManager cacheManager;
  private final Clock clock;

  static final String REPORTING_CURRENCY = "USD";

  // Demo trading partners (idempotency anchor = CUSTOMER_ANADOLU).
  static final String CUSTOMER_GLOBAL =
      "Global Fashion Wear Corp."; // created by DemoTransactionSeeder
  static final String CUSTOMER_ANADOLU = "Anadolu Tekstil A.Ş.";
  static final String CUSTOMER_EUROPA = "Europa Mode GmbH";
  static final String SUPPLIER_EGE = "Ege Pamuk Kooperatifi";
  static final String SUPPLIER_YARN = "Yarn Source Ltd.";
  static final String SUPPLIER_DYE = "Dye & Chem BV";

  /** All issue/payment offsets used below — every one gets an FX rate so conversions never miss. */
  private static final int[] FX_OFFSETS = {0, -5, -10, -20, -30, -38, -40, -45, -55, -70, -75};

  /**
   * Seeds the finance demo dataset for {@code tenantId}. Caller must have bound {@code tenantId} in
   * {@link com.fabricmanagement.common.infrastructure.persistence.TenantContext}.
   */
  public void seedFor(UUID tenantId) {
    if (!tradingPartnerService.searchByName(tenantId, CUSTOMER_ANADOLU).isEmpty()) {
      log.info("Finance demo data already exists for tenant: {}. Skipping.", tenantId);
      return;
    }

    log.info("Provisioning finance demo data for tenant: {}", tenantId);

    setReportingCurrencyUsd(tenantId);
    seedExchangeRates();

    LocalDate today = LocalDate.now(clock);

    // Trading partners (reuse the customer created by DemoTransactionSeeder).
    UUID cGlobal =
        ensurePartner(tenantId, CUSTOMER_GLOBAL, "US-GFW-DEMO", "USA", PartnerType.CUSTOMER);
    UUID cAnadolu =
        ensurePartner(tenantId, CUSTOMER_ANADOLU, "TR-ANA-DEMO", "TUR", PartnerType.CUSTOMER);
    UUID cEuropa =
        ensurePartner(tenantId, CUSTOMER_EUROPA, "DE-EUR-DEMO", "DEU", PartnerType.CUSTOMER);
    UUID sEge = ensurePartner(tenantId, SUPPLIER_EGE, "TR-EGE-DEMO", "TUR", PartnerType.SUPPLIER);
    UUID sYarn = ensurePartner(tenantId, SUPPLIER_YARN, "US-YRN-DEMO", "USA", PartnerType.SUPPLIER);
    UUID sDye = ensurePartner(tenantId, SUPPLIER_DYE, "NL-DYE-DEMO", "NLD", PartnerType.SUPPLIER);

    // ── SALES invoices (AR) — drive DSO, AR aging, cash-flow inflows, revenue trend, FX ──
    InvoiceDto s1 =
        issuedInvoice(cAnadolu, "SALES", "TRY", today.plusDays(-75), today.plusDays(-45), "850000");
    issuedInvoice(cGlobal, "SALES", "USD", today.plusDays(-55), today.plusDays(-25), "45000");
    issuedInvoice(cEuropa, "SALES", "EUR", today.plusDays(-30), today.plusDays(5), "60000");
    issuedInvoice(cAnadolu, "SALES", "TRY", today.plusDays(-10), today.plusDays(20), "320000");

    // ── PURCHASE invoices (AP) — drive DPO, AP aging, cash-flow outflows, FX ──
    InvoiceDto p1 =
        issuedInvoice(sEge, "PURCHASE", "TRY", today.plusDays(-70), today.plusDays(-40), "300000");
    issuedInvoice(sYarn, "PURCHASE", "USD", today.plusDays(-45), today.plusDays(-15), "28000");
    InvoiceDto p3 =
        issuedInvoice(sDye, "PURCHASE", "EUR", today.plusDays(-20), today.plusDays(10), "15000");

    // ── Payments — partial (S1, P3) + full (P1); same currency as the invoice ──
    pay(cAnadolu, "INBOUND", "TRY", "400000", today.plusDays(-40), s1.id());
    pay(sEge, "OUTBOUND", "TRY", "300000", today.plusDays(-38), p1.id());
    pay(sDye, "OUTBOUND", "EUR", "8000", today.plusDays(-5), p3.id());

    log.info("Successfully provisioned finance demo data for tenant: {}", tenantId);
  }

  private void setReportingCurrencyUsd(UUID tenantId) {
    organizationRepository
        .findRootOrganization(tenantId, OrganizationType.EXTERNAL_PARTNER)
        .ifPresent(
            org -> {
              org.setReportingCurrency(REPORTING_CURRENCY);
              organizationRepository.save(org);
            });
    // Evict any cached (possibly default) reporting currency captured before this write.
    var cache = cacheManager.getCache("tenantReportingCurrency");
    if (cache != null) {
      cache.evict(tenantId);
    }
  }

  private void seedExchangeRates() {
    LocalDate today = LocalDate.now(clock);
    for (int offset : FX_OFFSETS) {
      LocalDate date = today.plusDays(offset);
      // saveRate also stores the reverse direction (e.g. TRY→USD), which is what valuation needs.
      exchangeRateService.saveRate(
          REPORTING_CURRENCY, "TRY", new BigDecimal("38.00"), date, ExchangeRateSource.MANUAL);
      exchangeRateService.saveRate(
          REPORTING_CURRENCY, "EUR", new BigDecimal("0.92"), date, ExchangeRateSource.MANUAL);
    }
  }

  private UUID ensurePartner(
      UUID tenantId, String name, String taxId, String country, PartnerType type) {
    List<TradingPartnerDto> existing = tradingPartnerService.searchByName(tenantId, name);
    if (!existing.isEmpty()) {
      return existing.get(0).getId();
    }
    CreateTradingPartnerRequest req = new CreateTradingPartnerRequest();
    req.setCompanyName(name);
    req.setCustomName(name);
    req.setTaxId(taxId);
    req.setPartnerType(type);
    req.setCountry(country);
    return tradingPartnerService.createPartner(req).getId();
  }

  /** Creates a no-line invoice (subtotal == total, no tax/discount), then issues and sends it. */
  private InvoiceDto issuedInvoice(
      UUID partnerId, String type, String currency, LocalDate issue, LocalDate due, String total) {
    BigDecimal amount = new BigDecimal(total);
    CreateInvoiceRequest req =
        new CreateInvoiceRequest(
            partnerId,
            "DEMO",
            null,
            type,
            issue,
            due,
            amount,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            amount,
            currency,
            null,
            null,
            "Demo seeded invoice",
            null,
            null);
    InvoiceDto created = invoiceService.createInvoice(req);
    invoiceService.issueInvoice(created.id());
    return invoiceService.sendInvoice(created.id());
  }

  private void pay(
      UUID partnerId,
      String direction,
      String currency,
      String amount,
      LocalDate date,
      UUID invoiceId) {
    BigDecimal value = new BigDecimal(amount);
    CreatePaymentRequest req =
        new CreatePaymentRequest(
            partnerId,
            direction,
            "BANK_TRANSFER",
            value,
            currency,
            date,
            "DEMO-PAY",
            "Demo seeded payment",
            List.of(new CreateAllocationRequest(invoiceId, value)));
    paymentService.createPayment(req);
  }
}
