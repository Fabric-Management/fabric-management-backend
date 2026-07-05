package com.fabricmanagement.sales.quote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.pricing.app.DiscountPolicyService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService.PricingResult;
import com.fabricmanagement.sales.pricing.domain.DiscountPolicy;
import com.fabricmanagement.sales.quote.api.QuoteCreateRequest;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalStatus;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalToken;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.dto.QuoteResponse;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteRequest;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.salesproduct.app.SalesProductService;
import com.fabricmanagement.sales.salesproduct.dto.SalesProductDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

  @Mock private QuoteRepository quoteRepository;
  @Mock private PricingEngineService pricingEngineService;
  @Mock private SalesProductService catalogService;
  @Mock private DiscountPolicyService policyService;
  @Mock private ExchangeRateService exchangeRateService;
  @Mock private TenantReportingCurrencyPort tenantReportingCurrencyPort;
  @Mock private QuoteApprovalService quoteApprovalService;
  @Mock private TradingPartnerResolver tradingPartnerResolver;

  @InjectMocks private QuoteService quoteService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID quoteId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();
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
  @DisplayName("Should populate customer name on quote list response")
  void shouldPopulateCustomerNameOnQuoteListResponse() {
    PageRequest pageable = PageRequest.of(0, 20);
    when(quoteRepository.findAllByTenantIdAndIsActiveTrue(tenantId, pageable))
        .thenReturn(new PageImpl<>(List.of(quote), pageable, 1));
    when(tradingPartnerResolver.resolveDisplayNames(tenantId, List.of(customerId)))
        .thenReturn(Map.of(customerId, "Acme Textiles"));

    Page<QuoteResponse> page = quoteService.findAllResponses(pageable);

    assertEquals("Acme Textiles", page.getContent().get(0).getCustomerName());
  }

  @Test
  @DisplayName("Should keep customer name null when quote customer is missing")
  void shouldKeepCustomerNameNullWhenCustomerMissing() {
    PageRequest pageable = PageRequest.of(0, 20);
    when(quoteRepository.findAllByTenantIdAndIsActiveTrue(tenantId, pageable))
        .thenReturn(new PageImpl<>(List.of(quote), pageable, 1));
    when(tradingPartnerResolver.resolveDisplayNames(tenantId, List.of(customerId)))
        .thenReturn(Map.of());

    Page<QuoteResponse> page = quoteService.findAllResponses(pageable);

    assertNull(page.getContent().get(0).getCustomerName());
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
    verify(quoteRepository, times(2)).save(quoteCaptor.capture());
    List<Quote> savedQuotes = quoteCaptor.getAllValues();
    Quote savedRevision = savedQuotes.get(1);

    verify(quoteApprovalService).expirePendingTokensForQuote(tenantId, quoteId);
    assertEquals(revised, savedRevision);
    assertEquals("USD", savedRevision.getCurrency());
    assertEquals("USD", savedRevision.getLines().get(0).getCurrency());
    assertNotNull(savedRevision.getTotalAmount());
    assertNotNull(savedRevision.getReportingTotal());
  }

  @Test
  @DisplayName("Should update draft quote header fields without changing totals")
  void shouldUpdateDraftQuoteHeaderFieldsWithoutChangingTotals() {
    LocalDate validUntil = LocalDate.now().plusDays(30);
    QuoteLine line = quoteLine("GBP", "10.00", "2.000");
    line.setId(UUID.randomUUID());
    quote.addLine(line);
    quote.setTotalAmount(Money.of(new BigDecimal("20.00"), "GBP"));
    quote.setReportingTotal(
        ConvertedMoney.of(
            new BigDecimal("20.00"),
            "GBP",
            new BigDecimal("20.00"),
            "GBP",
            BigDecimal.ONE,
            LocalDate.now()));

    UpdateQuoteRequest req = new UpdateQuoteRequest();
    req.setValidUntil(validUntil);
    req.setPaymentTerms("Net 45");
    req.setLeadTimeDays(12);
    req.setNotes("Updated planner note");

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");

    Quote updated = quoteService.updateQuoteHeader(quoteId, req);

    assertEquals(validUntil, updated.getValidUntil());
    assertEquals("Net 45", updated.getPaymentTerms());
    assertEquals(12, updated.getLeadTimeDays());
    assertEquals("Updated planner note", updated.getNotes());
    assertEquals(0, updated.getTotalAmount().getAmount().compareTo(new BigDecimal("20.00")));
    assertEquals(
        0, updated.getReportingTotal().getConvertedAmount().compareTo(new BigDecimal("20.00")));
  }

  @Test
  @DisplayName("Should update line quantity and price, re-evaluate zone, and recompute totals")
  void shouldUpdateLineQuantityAndPriceReevaluateZoneAndRecomputeTotals() {
    UUID lineId = UUID.randomUUID();
    QuoteLine line = quoteLine("GBP", "100.00", "2.000");
    line.setId(lineId);
    quote.addLine(line);

    UpdateQuoteLineRequest req = new UpdateQuoteLineRequest();
    req.setRequestedQty(new BigDecimal("3.000"));
    req.setUnit("M");
    req.setOfferedPrice(new BigDecimal("80.00"));

    PricingResult managerApproval =
        PricingResult.builder()
            .discountRate(new BigDecimal("0.2000"))
            .profitMargin(new BigDecimal("0.9375"))
            .priceZone(QuotePriceZone.MANAGER_APPROVAL)
            .build();

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(pricingEngineService.evaluatePrice(
            eq(new BigDecimal("100.00")),
            eq(new BigDecimal("80.00")),
            eq(new BigDecimal("5.00")),
            any(DiscountPolicy.class)))
        .thenReturn(managerApproval);
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));

    Quote updated = quoteService.updateQuoteLine(quoteId, lineId, req);

    QuoteLine updatedLine = updated.getLines().get(0);
    assertEquals(0, updatedLine.getRequestedQty().compareTo(new BigDecimal("3.000")));
    assertEquals("M", updatedLine.getUnit());
    assertEquals(0, updatedLine.getOfferedPrice().compareTo(new BigDecimal("80.00")));
    assertEquals(QuotePriceZone.MANAGER_APPROVAL, updatedLine.getPriceZone());
    assertEquals(0, updatedLine.getDiscountRate().compareTo(new BigDecimal("0.2000")));
    assertEquals(0, updated.getTotalAmount().getAmount().compareTo(new BigDecimal("240.00")));
    assertEquals(
        0, updated.getReportingTotal().getConvertedAmount().compareTo(new BigDecimal("240.00")));
  }

  @Test
  @DisplayName("Should delete a line and recompute quote totals")
  void shouldDeleteLineAndRecomputeTotals() {
    UUID removedLineId = UUID.randomUUID();
    QuoteLine removedLine = quoteLine("GBP", "10.00", "2.000");
    removedLine.setId(removedLineId);
    QuoteLine remainingLine = quoteLine("GBP", "5.00", "3.000");
    remainingLine.setId(UUID.randomUUID());
    quote.addLine(removedLine);
    quote.addLine(remainingLine);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));

    Quote updated = quoteService.removeQuoteLine(quoteId, removedLineId);

    assertEquals(1, updated.getLines().size());
    assertEquals(remainingLine.getId(), updated.getLines().get(0).getId());
    assertEquals(0, updated.getTotalAmount().getAmount().compareTo(new BigDecimal("15.00")));
    assertEquals(
        0, updated.getReportingTotal().getConvertedAmount().compareTo(new BigDecimal("15.00")));
  }

  @Test
  @DisplayName("Should delete the last line and leave totals at zero")
  void shouldDeleteLastLineAndLeaveTotalsAtZero() {
    UUID lineId = UUID.randomUUID();
    QuoteLine line = quoteLine("GBP", "10.00", "2.000");
    line.setId(lineId);
    quote.addLine(line);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));

    Quote updated = quoteService.removeQuoteLine(quoteId, lineId);

    assertEquals(0, updated.getLines().size());
    assertEquals(0, updated.getTotalAmount().getAmount().compareTo(BigDecimal.ZERO));
    assertEquals(0, updated.getReportingTotal().getConvertedAmount().compareTo(BigDecimal.ZERO));
  }

  @Test
  @DisplayName("Should reject header updates for non-editable quote statuses")
  void shouldRejectHeaderUpdatesForNonEditableQuoteStatuses() {
    for (QuoteStatus status :
        List.of(QuoteStatus.APPROVED, QuoteStatus.PENDING_APPROVAL, QuoteStatus.SUPERSEDED)) {
      Quote lockedQuote = quote("GBP");
      lockedQuote.setStatus(status);
      lockedQuote.setPaymentTerms("Net 30");

      UpdateQuoteRequest req = new UpdateQuoteRequest();
      req.setPaymentTerms("Net 60");

      when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
          .thenReturn(Optional.of(lockedQuote));

      assertThrows(SalesDomainException.class, () -> quoteService.updateQuoteHeader(quoteId, req));

      assertEquals("Net 30", lockedQuote.getPaymentTerms());
    }

    verify(quoteRepository, never()).save(any(Quote.class));
  }

  @Test
  @DisplayName("Should reject line updates for non-editable quote statuses")
  void shouldRejectLineUpdatesForNonEditableQuoteStatuses() {
    UUID lineId = UUID.randomUUID();
    UpdateQuoteLineRequest req = new UpdateQuoteLineRequest();
    req.setRequestedQty(new BigDecimal("4.000"));
    req.setUnit("KG");
    req.setOfferedPrice(new BigDecimal("8.00"));

    for (QuoteStatus status :
        List.of(QuoteStatus.APPROVED, QuoteStatus.PENDING_APPROVAL, QuoteStatus.SUPERSEDED)) {
      Quote lockedQuote = quote("GBP");
      lockedQuote.setStatus(status);
      QuoteLine line = quoteLine("GBP", "10.00", "2.000");
      line.setId(lineId);
      lockedQuote.addLine(line);

      when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
          .thenReturn(Optional.of(lockedQuote));

      assertThrows(
          SalesDomainException.class, () -> quoteService.updateQuoteLine(quoteId, lineId, req));

      assertEquals(0, line.getRequestedQty().compareTo(new BigDecimal("2.000")));
      assertEquals(0, line.getOfferedPrice().compareTo(new BigDecimal("10.00")));
      assertEquals(QuotePriceZone.FREE, line.getPriceZone());
    }

    verify(policyService, never()).getActivePolicy(any());
    verify(pricingEngineService, never()).evaluatePrice(any(), any(), any(), any());
    verify(quoteRepository, never()).save(any(Quote.class));
  }

  @Test
  @DisplayName("Should reject line deletes for non-editable quote statuses")
  void shouldRejectLineDeletesForNonEditableQuoteStatuses() {
    UUID lineId = UUID.randomUUID();

    for (QuoteStatus status :
        List.of(QuoteStatus.APPROVED, QuoteStatus.PENDING_APPROVAL, QuoteStatus.SUPERSEDED)) {
      Quote lockedQuote = quote("GBP");
      lockedQuote.setStatus(status);
      QuoteLine line = quoteLine("GBP", "10.00", "2.000");
      line.setId(lineId);
      lockedQuote.addLine(line);

      when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
          .thenReturn(Optional.of(lockedQuote));

      assertThrows(SalesDomainException.class, () -> quoteService.removeQuoteLine(quoteId, lineId));

      assertEquals(1, lockedQuote.getLines().size());
    }

    verify(quoteRepository, never()).save(any(Quote.class));
  }

  @Test
  @DisplayName("Should preserve tenant isolation for quote mutators")
  void shouldPreserveTenantIsolationForQuoteMutators() {
    UUID lineId = UUID.randomUUID();
    UpdateQuoteRequest headerReq = new UpdateQuoteRequest();
    headerReq.setNotes("Cross-tenant update");
    UpdateQuoteLineRequest lineReq = new UpdateQuoteLineRequest();
    lineReq.setRequestedQty(new BigDecimal("2.000"));
    lineReq.setUnit("KG");
    lineReq.setOfferedPrice(new BigDecimal("12.00"));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.empty());

    assertThrows(
        SalesDomainException.class, () -> quoteService.updateQuoteHeader(quoteId, headerReq));
    assertThrows(
        SalesDomainException.class, () -> quoteService.updateQuoteLine(quoteId, lineId, lineReq));
    assertThrows(SalesDomainException.class, () -> quoteService.removeQuoteLine(quoteId, lineId));
    assertThrows(
        SalesDomainException.class, () -> quoteService.sendQuote(quoteId, "buyer@example.com"));

    verify(quoteRepository, times(4)).findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId);
    verify(quoteRepository, never()).save(any(Quote.class));
    verify(quoteApprovalService, never())
        .generateTokenForQuote(any(), any(QuoteApprovalChannel.class), any());
  }

  @Test
  @DisplayName("Should submit clean draft quote and mint email token when sending")
  void shouldSubmitCleanDraftQuoteAndMintEmailTokenWhenSending() {
    quote.addLine(quoteLine("GBP", "10.00", "2.000"));
    QuoteApprovalToken approvalToken = quoteApprovalToken("buyer@example.com");

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quoteApprovalService.generateTokenForQuote(
            quoteId, QuoteApprovalChannel.EMAIL, "buyer@example.com"))
        .thenReturn(approvalToken);

    QuoteApprovalToken result = quoteService.sendQuote(quoteId, "buyer@example.com");

    assertEquals(approvalToken, result);
    assertEquals(QuoteStatus.APPROVED, quote.getStatus());
    verify(quoteApprovalService)
        .generateTokenForQuote(quoteId, QuoteApprovalChannel.EMAIL, "buyer@example.com");
  }

  @Test
  @DisplayName("Should move manager approval quote to pending approval and not mint token")
  void shouldMoveManagerApprovalQuoteToPendingApprovalAndNotMintToken() {
    QuoteLine line = quoteLine("GBP", "10.00", "2.000");
    line.setPriceZone(QuotePriceZone.MANAGER_APPROVAL);
    quote.addLine(line);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class, () -> quoteService.sendQuote(quoteId, "buyer@example.com"));

    assertEquals("SALES_006_QUOTE_NEEDS_INTERNAL_APPROVAL", ex.getErrorCode());
    assertEquals(QuoteStatus.PENDING_APPROVAL, quote.getStatus());
    verify(quoteApprovalService, never())
        .generateTokenForQuote(any(), any(QuoteApprovalChannel.class), any());
  }

  @Test
  @DisplayName("Should return needs approval for blocked quote and not mint token")
  void shouldReturnNeedsApprovalForBlockedQuoteAndNotMintToken() {
    QuoteLine line = quoteLine("GBP", "10.00", "2.000");
    line.setPriceZone(QuotePriceZone.BLOCKED);
    quote.addLine(line);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class, () -> quoteService.sendQuote(quoteId, "buyer@example.com"));

    assertEquals("SALES_006_QUOTE_NEEDS_INTERNAL_APPROVAL", ex.getErrorCode());
    assertEquals(QuoteStatus.DRAFT, quote.getStatus());
    verify(quoteRepository, never()).save(any(Quote.class));
    verify(quoteApprovalService, never())
        .generateTokenForQuote(any(), any(QuoteApprovalChannel.class), any());
  }

  private Quote quote(String currency) {
    Quote q = new Quote();
    q.setId(quoteId);
    q.setTenantId(tenantId);
    q.setQuoteNumber("Q-2026-001");
    q.setCustomerId(customerId);
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

  private QuoteApprovalToken quoteApprovalToken(String sentTo) {
    QuoteApprovalToken token = new QuoteApprovalToken();
    token.setTenantId(tenantId);
    token.setQuoteId(quoteId);
    token.setToken("approval-token");
    token.setChannel(QuoteApprovalChannel.EMAIL);
    token.setSentTo(sentTo);
    token.setExpiresAt(Instant.now().plusSeconds(3600));
    token.setStatus(QuoteApprovalStatus.PENDING);
    return token;
  }
}
