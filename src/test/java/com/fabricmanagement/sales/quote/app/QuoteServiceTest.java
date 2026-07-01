package com.fabricmanagement.sales.quote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.pricing.app.DiscountPolicyService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService.PricingResult;
import com.fabricmanagement.sales.pricing.domain.DiscountPolicy;
import com.fabricmanagement.sales.quote.api.QuoteCreateRequest;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.salesproduct.app.SalesProductService;
import com.fabricmanagement.sales.salesproduct.dto.SalesProductDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

  @Mock private QuoteRepository quoteRepository;
  @Mock private PricingEngineService pricingEngineService;
  @Mock private SalesProductService catalogService;
  @Mock private DiscountPolicyService policyService;
  @Mock private ExchangeRateService exchangeRateService;
  @Mock private TenantReportingCurrencyPort tenantReportingCurrencyPort;

  @InjectMocks private QuoteService quoteService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID quoteId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private Quote quote;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    quote = quote("GBP");
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Should create quote with header currency")
  void shouldCreateQuoteWithCurrency() {
    QuoteCreateRequest req = new QuoteCreateRequest();
    req.setCustomerId(UUID.randomUUID());
    req.setAssignedToId(UUID.randomUUID());
    req.setModuleType("FABRIC");
    req.setQuoteNumber("Q-2026-001");
    req.setCurrency("GBP");
    req.setValidUntil(LocalDate.now().plusDays(5));

    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));

    Quote created = quoteService.createQuote(req);

    assertEquals("GBP", created.getCurrency());
    assertEquals(QuoteStatus.DRAFT, created.getStatus());
  }

  @Test
  @DisplayName("Should compute mixed-currency native and reporting totals")
  void shouldComputeMixedCurrencyTotals() {
    LocalDate docDate = LocalDate.of(2026, 7, 1);
    quote.setCreatedAt(Instant.parse("2026-07-01T09:00:00Z"));
    quote.addLine(quoteLine("EUR", "20.00", "2.000"));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(catalogService.getActiveByProductId(productId)).thenReturn(product("USD", "30.00"));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(pricingEngineService.evaluatePrice(any(), any(), any(), any()))
        .thenReturn(pricingResult());
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");
    when(exchangeRateService.convert(
            eq(tenantId), any(BigDecimal.class), eq("EUR"), eq("GBP"), eq(docDate)))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("40.00000"),
                "EUR",
                new BigDecimal("32.0000"),
                "GBP",
                new BigDecimal("0.80000000"),
                docDate));
    when(exchangeRateService.convert(
            eq(tenantId), any(BigDecimal.class), eq("USD"), eq("GBP"), eq(docDate)))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("60.00000"),
                "USD",
                new BigDecimal("48.0000"),
                "GBP",
                new BigDecimal("0.80000000"),
                docDate));

    Quote updated =
        quoteService.addQuoteLine(
            quoteId, productId, new BigDecimal("2.000"), "KG", new BigDecimal("30.00"));

    assertNotNull(updated.getTotalAmount());
    assertEquals(0, updated.getTotalAmount().getAmount().compareTo(new BigDecimal("80.00")));
    assertEquals("GBP", updated.getTotalAmount().getCurrency().getCurrencyCode());
    assertNotNull(updated.getReportingTotal());
    assertEquals(
        0, updated.getReportingTotal().getOriginalAmount().compareTo(new BigDecimal("80.0000")));
    assertEquals("GBP", updated.getReportingTotal().getOriginalCurrency());
    assertEquals(
        0, updated.getReportingTotal().getConvertedAmount().compareTo(new BigDecimal("80.0000")));
    assertEquals("GBP", updated.getReportingTotal().getConvertedCurrency());
    assertEquals(BigDecimal.ONE, updated.getReportingTotal().getExchangeRate());
    assertEquals(docDate, updated.getReportingTotal().getRateDate());
  }

  @Test
  @DisplayName("Should compute single-currency totals with rate 1")
  void shouldComputeSingleCurrencyTotalsWithRateOne() {
    quote.setCreatedAt(Instant.parse("2026-07-01T09:00:00Z"));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(catalogService.getActiveByProductId(productId)).thenReturn(product("GBP", "15.00"));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(pricingEngineService.evaluatePrice(any(), any(), any(), any()))
        .thenReturn(pricingResult());
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");

    Quote updated =
        quoteService.addQuoteLine(
            quoteId, productId, new BigDecimal("2.000"), "KG", new BigDecimal("12.00"));

    assertEquals(0, updated.getTotalAmount().getAmount().compareTo(new BigDecimal("24.00")));
    assertEquals("GBP", updated.getTotalAmount().getCurrency().getCurrencyCode());
    assertEquals(
        0, updated.getReportingTotal().getConvertedAmount().compareTo(new BigDecimal("24.0000")));
    assertEquals(BigDecimal.ONE, updated.getReportingTotal().getExchangeRate());
    verify(exchangeRateService, never()).convert(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Should convert native total to tenant reporting currency")
  void shouldConvertNativeTotalToReportingCurrency() {
    LocalDate docDate = LocalDate.of(2026, 7, 1);
    quote.setCurrency("USD");
    quote.setCreatedAt(Instant.parse("2026-07-01T09:00:00Z"));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(catalogService.getActiveByProductId(productId)).thenReturn(product("USD", "15.00"));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(pricingEngineService.evaluatePrice(any(), any(), any(), any()))
        .thenReturn(pricingResult());
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");
    when(exchangeRateService.convert(
            eq(tenantId), any(BigDecimal.class), eq("USD"), eq("GBP"), eq(docDate)))
        .thenReturn(
            ConvertedMoney.of(
                new BigDecimal("24.00000"),
                "USD",
                new BigDecimal("19.2000"),
                "GBP",
                new BigDecimal("0.80000000"),
                docDate));

    Quote updated =
        quoteService.addQuoteLine(
            quoteId, productId, new BigDecimal("2.000"), "KG", new BigDecimal("12.00"));

    assertEquals(0, updated.getTotalAmount().getAmount().compareTo(new BigDecimal("24.00")));
    assertEquals("USD", updated.getTotalAmount().getCurrency().getCurrencyCode());
    assertEquals(
        0, updated.getReportingTotal().getOriginalAmount().compareTo(new BigDecimal("24.00000")));
    assertEquals("USD", updated.getReportingTotal().getOriginalCurrency());
    assertEquals(
        0, updated.getReportingTotal().getConvertedAmount().compareTo(new BigDecimal("19.2000")));
    assertEquals("GBP", updated.getReportingTotal().getConvertedCurrency());
    assertEquals(
        0, updated.getReportingTotal().getExchangeRate().compareTo(new BigDecimal("0.80000000")));
    assertEquals(docDate, updated.getReportingTotal().getRateDate());
  }

  @Test
  @DisplayName("Should fail closed when exchange rate is missing")
  void shouldFailClosedWhenExchangeRateIsMissing() {
    LocalDate docDate = LocalDate.of(2026, 7, 1);
    quote.setCreatedAt(Instant.parse("2026-07-01T09:00:00Z"));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(catalogService.getActiveByProductId(productId)).thenReturn(product("USD", "15.00"));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(pricingEngineService.evaluatePrice(any(), any(), any(), any()))
        .thenReturn(pricingResult());
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");
    when(exchangeRateService.convert(
            eq(tenantId), any(BigDecimal.class), eq("USD"), eq("GBP"), eq(docDate)))
        .thenThrow(new ExchangeRateRequiredException("USD", "GBP", docDate));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class,
            () ->
                quoteService.addQuoteLine(
                    quoteId, productId, new BigDecimal("2.000"), "KG", new BigDecimal("12.00")));

    assertEquals(
        "No exchange rate for USD->GBP on 2026-07-01; seed a rate before saving this quote",
        ex.getMessage());
    verify(quoteRepository, never()).save(any(Quote.class));
  }

  @Test
  @DisplayName("Should derive line currency from catalog product")
  void shouldDeriveLineCurrencyFromCatalog() {
    quote.setCurrency("USD");

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(catalogService.getActiveByProductId(productId)).thenReturn(product("USD", "15.00"));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(pricingEngineService.evaluatePrice(any(), any(), any(), any()))
        .thenReturn(pricingResult());
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("USD");

    Quote updated =
        quoteService.addQuoteLine(
            quoteId, productId, new BigDecimal("2.000"), "KG", new BigDecimal("12.00"));

    assertEquals("USD", updated.getLines().get(0).getCurrency());
  }

  @Test
  @DisplayName("Should carry line currency when revising quote")
  void shouldCarryLineCurrencyWhenRevisingQuote() {
    quote.setCurrency("USD");
    quote.setStatus(QuoteStatus.APPROVED);
    quote.addLine(quoteLine("USD", "12.00", "2.000"));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("USD");

    Quote revised = quoteService.reviseQuote(quoteId);

    ArgumentCaptor<Quote> quoteCaptor = ArgumentCaptor.forClass(Quote.class);
    verify(quoteRepository, org.mockito.Mockito.times(2)).save(quoteCaptor.capture());
    List<Quote> savedQuotes = quoteCaptor.getAllValues();
    Quote savedRevision = savedQuotes.get(1);

    assertEquals(revised, savedRevision);
    assertEquals("USD", savedRevision.getCurrency());
    assertEquals("USD", savedRevision.getLines().get(0).getCurrency());
    assertNotNull(savedRevision.getTotalAmount());
    assertNotNull(savedRevision.getReportingTotal());
  }

  private Quote quote(String currency) {
    Quote q = new Quote();
    q.setId(quoteId);
    q.setTenantId(tenantId);
    q.setQuoteNumber("Q-2026-001");
    q.setCustomerId(UUID.randomUUID());
    q.setAssignedToId(UUID.randomUUID());
    q.setModuleType("FABRIC");
    q.setCurrency(currency);
    q.setEstimatedUnitCost(new BigDecimal("5.00"));
    q.setValidUntil(LocalDate.now().plusDays(10));
    q.setStatus(QuoteStatus.DRAFT);
    q.setRevisionNumber(1);
    return q;
  }

  private QuoteLine quoteLine(String currency, String offeredPrice, String requestedQty) {
    QuoteLine line = new QuoteLine();
    line.setTenantId(tenantId);
    line.setProductId(UUID.randomUUID());
    line.setRequestedQty(new BigDecimal(requestedQty));
    line.setUnit("KG");
    line.setListPrice(new BigDecimal(offeredPrice));
    line.setOfferedPrice(new BigDecimal(offeredPrice));
    line.setCurrency(currency);
    line.setDiscountRate(BigDecimal.ZERO);
    line.setProfitMargin(BigDecimal.ONE);
    line.setPriceZone(QuotePriceZone.FREE);
    return line;
  }

  private SalesProductDto product(String currency, String listPrice) {
    return new SalesProductDto(
        UUID.randomUUID(),
        productId,
        "FABRIC",
        new BigDecimal(listPrice),
        currency,
        null,
        null,
        null,
        "{}",
        "[]",
        true,
        tenantId);
  }

  private PricingResult pricingResult() {
    return PricingResult.builder()
        .discountRate(BigDecimal.ZERO)
        .profitMargin(BigDecimal.ONE)
        .priceZone(QuotePriceZone.FREE)
        .build();
  }
}
