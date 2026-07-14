package com.fabricmanagement.sales.quote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.platform.tradingpartner.app.PartnerContactService;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContact;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContactRole;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.user.app.UserDisplayNameResolver;
import com.fabricmanagement.production.execution.batch.api.BatchLotQuantityIntentPort;
import com.fabricmanagement.production.execution.batch.api.BatchLotQuantityIntentPort.LotIntentCoverage;
import com.fabricmanagement.production.execution.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.sales.color.app.SalesColorService;
import com.fabricmanagement.sales.color.app.SalesColorSnapshot;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.lot.app.SalesLotService;
import com.fabricmanagement.sales.pricing.app.DiscountPolicyService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService;
import com.fabricmanagement.sales.pricing.app.PricingEngineService.PricingResult;
import com.fabricmanagement.sales.pricing.domain.DiscountPolicy;
import com.fabricmanagement.sales.qualitygrade.app.SalesQualityGradeService;
import com.fabricmanagement.sales.qualitygrade.app.SalesQualityGradeSnapshot;
import com.fabricmanagement.sales.quote.api.QuoteCreateRequest;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalStatus;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalToken;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import com.fabricmanagement.sales.quote.domain.QuoteSendRequest;
import com.fabricmanagement.sales.quote.domain.QuoteSendRequestStatus;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.domain.event.QuoteSendRequestRejectedEvent;
import com.fabricmanagement.sales.quote.domain.event.QuoteSendRequestedEvent;
import com.fabricmanagement.sales.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotPieceSnapshot;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSelectionRequest;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSnapshot;
import com.fabricmanagement.sales.quote.dto.QuoteLineLotSnapshotCodec;
import com.fabricmanagement.sales.quote.dto.QuoteResponse;
import com.fabricmanagement.sales.quote.dto.QuoteStatusCountsResponse;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteLineRequest;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteRequest;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.quote.infra.repository.QuoteSendRequestRepository;
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
import org.springframework.context.ApplicationEventPublisher;
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
  @Mock private PartnerContactService partnerContactService;
  @Mock private UserDisplayNameResolver userDisplayNameResolver;
  @Mock private QuoteSendRequestRepository quoteSendRequestRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private SalesQualityGradeService salesQualityGradeService;
  @Mock private SalesColorService salesColorService;
  @Mock private SalesLotService salesLotService;
  @Mock private StockUnitSoftHoldPort stockUnitSoftHoldPort;
  @Mock private BatchLotQuantityIntentPort batchLotQuantityIntentPort;

  @InjectMocks private QuoteService quoteService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID quoteId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();
  private final UUID contactId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private Quote quote;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
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
  @DisplayName("Should filter quote list by status without searching partners")
  void shouldFilterQuoteListByStatus() {
    PageRequest pageable = PageRequest.of(0, 20);
    when(quoteRepository.findAllByTenantIdAndStatusAndIsActiveTrue(
            tenantId, QuoteStatus.APPROVED, pageable))
        .thenReturn(Page.empty(pageable));

    quoteService.findAllResponses(QuoteStatus.APPROVED, null, pageable);

    verify(tradingPartnerResolver, never()).findCustomerIdsByNameContains(any(), any());
  }

  @Test
  @DisplayName("Should treat a trimmed query shorter than two characters as unfiltered")
  void shouldTreatShortQueryAsUnfiltered() {
    PageRequest pageable = PageRequest.of(0, 20);
    when(quoteRepository.findAllByTenantIdAndIsActiveTrue(tenantId, pageable))
        .thenReturn(Page.empty(pageable));

    quoteService.findAllResponses(null, " a ", pageable);

    verify(quoteRepository).findAllByTenantIdAndIsActiveTrue(tenantId, pageable);
    verify(tradingPartnerResolver, never()).findCustomerIdsByNameContains(any(), any());
  }

  @Test
  @DisplayName("Should search quote number only when no customer IDs match")
  void shouldSearchQuoteNumberWhenCustomerIdsAreEmpty() {
    PageRequest pageable = PageRequest.of(0, 20);
    when(tradingPartnerResolver.findCustomerIdsByNameContains(tenantId, "50%_"))
        .thenReturn(List.of());
    when(quoteRepository.searchByQuoteNumber(tenantId, null, "%50\\%\\_%", '\\', pageable))
        .thenReturn(Page.empty(pageable));

    quoteService.findAllResponses(null, " 50%_ ", pageable);

    verify(quoteRepository, never())
        .searchByQuoteNumberOrCustomerId(any(), any(), any(), anyChar(), any(), any());
  }

  @Test
  @DisplayName("Should combine status with quote-number or customer-name search")
  void shouldCombineStatusAndCustomerSearch() {
    PageRequest pageable = PageRequest.of(0, 20);
    UUID matchingCustomerId = UUID.randomUUID();
    when(tradingPartnerResolver.findCustomerIdsByNameContains(tenantId, "Acme"))
        .thenReturn(List.of(matchingCustomerId));
    when(quoteRepository.searchByQuoteNumberOrCustomerId(
            tenantId, QuoteStatus.DRAFT, "%Acme%", '\\', List.of(matchingCustomerId), pageable))
        .thenReturn(Page.empty(pageable));

    quoteService.findAllResponses(QuoteStatus.DRAFT, "Acme", pageable);

    verify(quoteRepository)
        .searchByQuoteNumberOrCustomerId(
            tenantId, QuoteStatus.DRAFT, "%Acme%", '\\', List.of(matchingCustomerId), pageable);
  }

  @Test
  @DisplayName("Should zero-fill every quote status count")
  void shouldZeroFillEveryQuoteStatusCount() {
    QuoteRepository.StatusCountProjection draft = mock(QuoteRepository.StatusCountProjection.class);
    QuoteRepository.StatusCountProjection expired =
        mock(QuoteRepository.StatusCountProjection.class);
    when(draft.getStatus()).thenReturn(QuoteStatus.DRAFT);
    when(draft.getCount()).thenReturn(4L);
    when(expired.getStatus()).thenReturn(QuoteStatus.EXPIRED);
    when(expired.getCount()).thenReturn(2L);
    when(quoteRepository.countActiveByStatus(tenantId)).thenReturn(List.of(draft, expired));

    QuoteStatusCountsResponse counts = quoteService.getStatusCounts();

    assertEquals(4L, counts.draft());
    assertEquals(2L, counts.expired());
    assertEquals(0L, counts.converted());
    assertEquals(0L, counts.countFor(QuoteStatus.SUPERSEDED));
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
  @DisplayName(
      "Should snapshot selected quality grade on quote line without mutating offered price")
  void shouldSnapshotSelectedQualityGradeOnQuoteLine() {
    UUID qualityGradeId = UUID.randomUUID();
    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setProductId(productId);
    req.setRequestedQty(new BigDecimal("2.000"));
    req.setUnit("KG");
    req.setOfferedPrice(new BigDecimal("12.00"));
    req.setQualityGradeId(qualityGradeId);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(catalogService.getActiveByProductId(productId)).thenReturn(product("GBP", "15.00"));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(pricingEngineService.evaluatePrice(any(), any(), any(), any()))
        .thenReturn(pricingResult());
    when(salesQualityGradeService.resolveSnapshot(qualityGradeId))
        .thenReturn(
            new SalesQualityGradeSnapshot(qualityGradeId, "A", "Grade A", new BigDecimal("1.125")));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");

    Quote updated = quoteService.addQuoteLine(quoteId, req);

    QuoteLine line = updated.getLines().get(0);
    assertEquals(qualityGradeId, line.getQualityGradeId());
    assertEquals("A", line.getQualityGradeCode());
    assertEquals("Grade A", line.getQualityGradeName());
    assertEquals(0, line.getQualityPriceFactor().compareTo(new BigDecimal("1.125")));
    assertEquals(0, line.getOfferedPrice().compareTo(new BigDecimal("12.00")));
  }

  @Test
  @DisplayName("Should reject unknown quality grade without saving quote line")
  void shouldRejectUnknownQualityGradeWithoutSavingQuoteLine() {
    UUID qualityGradeId = UUID.randomUUID();
    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setProductId(productId);
    req.setRequestedQty(new BigDecimal("2.000"));
    req.setUnit("KG");
    req.setOfferedPrice(new BigDecimal("12.00"));
    req.setQualityGradeId(qualityGradeId);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(catalogService.getActiveByProductId(productId)).thenReturn(product("GBP", "15.00"));
    when(salesQualityGradeService.resolveSnapshot(qualityGradeId))
        .thenThrow(new NotFoundException("Quality grade not found: " + qualityGradeId));

    assertThrows(NotFoundException.class, () -> quoteService.addQuoteLine(quoteId, req));

    verify(quoteRepository, never()).save(any(Quote.class));
  }

  @Test
  @DisplayName("Should snapshot selected color on quote line")
  void shouldSnapshotSelectedColorOnQuoteLine() {
    UUID colorId = UUID.randomUUID();
    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setProductId(productId);
    req.setRequestedQty(new BigDecimal("2.000"));
    req.setUnit("KG");
    req.setOfferedPrice(new BigDecimal("12.00"));
    req.setColorId(colorId);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(catalogService.getActiveByProductId(productId)).thenReturn(product("GBP", "15.00"));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(pricingEngineService.evaluatePrice(any(), any(), any(), any()))
        .thenReturn(pricingResult());
    when(salesColorService.resolveNewSelectionSnapshot(colorId))
        .thenReturn(new SalesColorSnapshot(colorId, "NAVY-01", "Navy", "#001F3F"));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");

    Quote updated = quoteService.addQuoteLine(quoteId, req);

    QuoteLine line = updated.getLines().get(0);
    assertEquals(colorId, line.getColorId());
    assertEquals("NAVY-01", line.getColorCode());
    assertEquals("Navy", line.getColorName());
    assertEquals("#001F3F", line.getColorHex());
  }

  @Test
  @DisplayName("Should snapshot selected lots and place soft holds after quote line save")
  void shouldSnapshotSelectedLotsAndPlaceSoftHoldsAfterQuoteLineSave() {
    UUID lineId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    UUID stockUnitId = UUID.randomUUID();
    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setProductId(productId);
    req.setRequestedQty(new BigDecimal("120.000"));
    req.setUnit("M");
    req.setOfferedPrice(new BigDecimal("12.00"));
    req.setSelectedLots(List.of(new QuoteLineLotSelectionRequest(lotId, List.of(stockUnitId))));
    List<QuoteLineLotSnapshot> snapshots =
        List.of(
            new QuoteLineLotSnapshot(
                lotId,
                "LOT-001",
                null,
                null,
                "LENGTH",
                "M",
                List.of(
                    new QuoteLineLotPieceSnapshot(
                        stockUnitId, "ROLL-001", new BigDecimal("120.000"), "M")),
                new BigDecimal("120.000")));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class)))
        .thenAnswer(
            inv -> {
              Quote saved = inv.getArgument(0);
              saved.getLines().get(0).setId(lineId);
              return saved;
            });
    when(catalogService.getActiveByProductId(productId)).thenReturn(product("GBP", "15.00"));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(pricingEngineService.evaluatePrice(any(), any(), any(), any()))
        .thenReturn(pricingResult());
    when(salesLotService.resolveNewSelectionSnapshots(req.getSelectedLots())).thenReturn(snapshots);
    when(batchLotQuantityIntentPort.checkCoverage(eq(null), any()))
        .thenReturn(new LotIntentCoverage(true));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");

    Quote updated = quoteService.addQuoteLine(quoteId, req);

    QuoteLine line = updated.getLines().get(0);
    assertEquals(snapshots, QuoteLineLotSnapshotCodec.fromJson(line.getLotSnapshot()));
    assertEquals(0, line.getRequestedQty().compareTo(new BigDecimal("120.000")));
    verify(stockUnitSoftHoldPort).replaceHolds(lineId, List.of(stockUnitId));
    verify(batchLotQuantityIntentPort)
        .replaceIntents(eq(quoteId), any(), eq(lineId), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Should reject selected lot quantities that do not equal requested quantity")
  void shouldRejectSelectedLotQuantitiesThatDoNotEqualRequestedQuantity() {
    UUID lotId = UUID.randomUUID();
    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setProductId(productId);
    req.setRequestedQty(new BigDecimal("120.000"));
    req.setUnit("M");
    req.setOfferedPrice(new BigDecimal("12.00"));
    req.setSelectedLots(
        List.of(new QuoteLineLotSelectionRequest(lotId, List.of(), new BigDecimal("100.000"))));
    List<QuoteLineLotSnapshot> snapshots =
        List.of(
            new QuoteLineLotSnapshot(
                lotId, "LOT-001", null, null, "LENGTH", "M", List.of(), new BigDecimal("100.000")));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(catalogService.getActiveByProductId(productId)).thenReturn(product("GBP", "15.00"));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(salesLotService.resolveNewSelectionSnapshots(req.getSelectedLots())).thenReturn(snapshots);

    SalesDomainException ex =
        assertThrows(SalesDomainException.class, () -> quoteService.addQuoteLine(quoteId, req));

    assertEquals("SALES_017_LOT_INTENT_QUANTITY_MISMATCH", ex.getErrorCode());
    assertEquals(422, ex.getHttpStatus());
    verify(quoteRepository, never()).save(any(Quote.class));
  }

  @Test
  @DisplayName("Should require delivery status when lot free stock does not cover line")
  void shouldRequireDeliveryStatusWhenLotFreeStockDoesNotCoverLine() {
    UUID lotId = UUID.randomUUID();
    AddQuoteLineRequest req = new AddQuoteLineRequest();
    req.setProductId(productId);
    req.setRequestedQty(new BigDecimal("120.000"));
    req.setUnit("M");
    req.setOfferedPrice(new BigDecimal("12.00"));
    req.setSelectedLots(
        List.of(new QuoteLineLotSelectionRequest(lotId, List.of(), new BigDecimal("120.000"))));
    List<QuoteLineLotSnapshot> snapshots =
        List.of(
            new QuoteLineLotSnapshot(
                lotId, "LOT-001", null, null, "LENGTH", "M", List.of(), new BigDecimal("120.000")));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(catalogService.getActiveByProductId(productId)).thenReturn(product("GBP", "15.00"));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(salesLotService.resolveNewSelectionSnapshots(req.getSelectedLots())).thenReturn(snapshots);
    when(batchLotQuantityIntentPort.checkCoverage(eq(null), any()))
        .thenReturn(new LotIntentCoverage(false));

    SalesDomainException ex =
        assertThrows(SalesDomainException.class, () -> quoteService.addQuoteLine(quoteId, req));

    assertEquals("SALES_016_DELIVERY_STATUS_REQUIRED", ex.getErrorCode());
    assertEquals(422, ex.getHttpStatus());
    verify(quoteRepository, never()).save(any(Quote.class));
  }

  @Test
  @DisplayName("Should update lot snapshot and keep offered spec unchanged")
  void shouldUpdateLotSnapshotAndKeepOfferedSpecUnchanged() {
    UUID lineId = UUID.randomUUID();
    UUID qualityGradeId = UUID.randomUUID();
    UUID colorId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    UUID stockUnitId = UUID.randomUUID();
    QuoteLine line = quoteLine("GBP", "100.00", "2.000");
    line.setId(lineId);
    line.applyQualityGrade(qualityGradeId, "A", "Grade A", new BigDecimal("1.125"));
    line.applyColor(colorId, "NAVY-01", "Navy", "#001F3F");
    quote.addLine(line);

    UpdateQuoteLineRequest req = new UpdateQuoteLineRequest();
    req.setQualityGradeId(qualityGradeId);
    req.setColorId(colorId);
    req.setRequestedQty(new BigDecimal("250.000"));
    req.setUnit("M");
    req.setOfferedPrice(new BigDecimal("80.00"));
    req.setSelectedLots(
        List.of(
            new QuoteLineLotSelectionRequest(
                lotId, List.of(stockUnitId), new BigDecimal("250.000"))));
    List<QuoteLineLotSnapshot> snapshots =
        List.of(
            new QuoteLineLotSnapshot(
                lotId,
                "LOT-002",
                null,
                null,
                "LENGTH",
                "M",
                List.of(
                    new QuoteLineLotPieceSnapshot(
                        stockUnitId, "ROLL-002", new BigDecimal("300.000"), "M")),
                new BigDecimal("250.000")));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(policyService.getActivePolicy("FABRIC")).thenReturn(new DiscountPolicy());
    when(pricingEngineService.evaluatePrice(any(), any(), any(), any()))
        .thenReturn(pricingResult());
    when(salesQualityGradeService.resolveUpdateSnapshot(qualityGradeId, line))
        .thenReturn(
            new SalesQualityGradeSnapshot(qualityGradeId, "A", "Grade A", new BigDecimal("1.125")));
    when(salesColorService.resolveUpdateSnapshot(colorId, line))
        .thenReturn(new SalesColorSnapshot(colorId, "NAVY-01", "Navy", "#001F3F"));
    when(salesLotService.resolveUpdateSelectionSnapshots(
            req.getSelectedLots(), line.getLotSnapshot()))
        .thenReturn(snapshots);
    when(batchLotQuantityIntentPort.checkCoverage(eq(lineId), any()))
        .thenReturn(new LotIntentCoverage(true));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));

    Quote updated = quoteService.updateQuoteLine(quoteId, lineId, req);

    QuoteLine updatedLine = updated.getLines().get(0);
    assertEquals(qualityGradeId, updatedLine.getQualityGradeId());
    assertEquals(colorId, updatedLine.getColorId());
    assertEquals(0, updatedLine.getRequestedQty().compareTo(new BigDecimal("250.000")));
    assertEquals(snapshots, QuoteLineLotSnapshotCodec.fromJson(updatedLine.getLotSnapshot()));
    verify(stockUnitSoftHoldPort).replaceHolds(lineId, List.of(stockUnitId));
    verify(batchLotQuantityIntentPort)
        .replaceIntents(eq(quoteId), any(), eq(lineId), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Should carry line currency when revising quote")
  void shouldCarryLineCurrencyWhenRevisingQuote() {
    quote.setCurrency("USD");
    quote.setStatus(QuoteStatus.APPROVED);
    UUID qualityGradeId = UUID.randomUUID();
    UUID colorId = UUID.randomUUID();
    QuoteLine sourceLine = quoteLine("USD", "12.00", "2.000");
    sourceLine.applyQualityGrade(qualityGradeId, "A", "Grade A", new BigDecimal("1.125"));
    sourceLine.applyColor(colorId, "NAVY-01", "Navy", "#001F3F");
    quote.addLine(sourceLine);

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
    QuoteLine revisedLine = savedRevision.getLines().get(0);
    assertEquals("USD", revisedLine.getCurrency());
    assertEquals(qualityGradeId, revisedLine.getQualityGradeId());
    assertEquals("A", revisedLine.getQualityGradeCode());
    assertEquals("Grade A", revisedLine.getQualityGradeName());
    assertEquals(0, revisedLine.getQualityPriceFactor().compareTo(new BigDecimal("1.125")));
    assertEquals(colorId, revisedLine.getColorId());
    assertEquals("NAVY-01", revisedLine.getColorCode());
    assertEquals("Navy", revisedLine.getColorName());
    assertEquals("#001F3F", revisedLine.getColorHex());
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
  @DisplayName("Should resync lot-intent expiry when header validUntil changes")
  void shouldResyncIntentExpiryWhenValidUntilChanges() {
    quote.setValidUntil(LocalDate.now().plusDays(10));
    LocalDate newValidUntil = LocalDate.now().plusDays(30);

    UpdateQuoteRequest req = new UpdateQuoteRequest();
    req.setValidUntil(newValidUntil);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");

    quoteService.updateQuoteHeader(quoteId, req);

    verify(batchLotQuantityIntentPort).resyncExpiry(quoteId, newValidUntil);
  }

  @Test
  @DisplayName("Should not resync lot-intent expiry when header validUntil is unchanged")
  void shouldNotResyncIntentExpiryWhenValidUntilUnchanged() {
    LocalDate validUntil = LocalDate.now().plusDays(10);
    quote.setValidUntil(validUntil);

    UpdateQuoteRequest req = new UpdateQuoteRequest();
    req.setValidUntil(validUntil);
    req.setNotes("Only the note changes");

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");

    quoteService.updateQuoteHeader(quoteId, req);

    verify(batchLotQuantityIntentPort, never()).resyncExpiry(any(), any());
  }

  @Test
  @DisplayName("Should update customer and currency before quote has lines")
  void shouldUpdateCustomerAndCurrencyBeforeQuoteHasLines() {
    UUID newCustomerId = UUID.randomUUID();
    LocalDate docDate = LocalDate.of(2026, 7, 1);
    quote.setCreatedAt(Instant.parse("2026-07-01T09:00:00Z"));

    UpdateQuoteRequest req = new UpdateQuoteRequest();
    req.setCustomerId(newCustomerId);
    req.setCurrency("USD");
    req.setPaymentTerms("Net 15");

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("GBP");
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tradingPartnerResolver.resolveDisplayNames(tenantId, List.of(newCustomerId)))
        .thenReturn(Map.of(newCustomerId, "New Customer Ltd"));

    Quote updated = quoteService.updateQuoteHeader(quoteId, req);
    QuoteResponse response = quoteService.toResponse(updated);

    assertEquals(newCustomerId, updated.getCustomerId());
    assertEquals("USD", updated.getCurrency());
    assertEquals("Net 15", updated.getPaymentTerms());
    assertEquals(0, updated.getLines().size());
    assertEquals(0, updated.getTotalAmount().getAmount().compareTo(BigDecimal.ZERO));
    assertEquals("USD", updated.getTotalAmount().getCurrency().getCurrencyCode());
    assertEquals(BigDecimal.ZERO, updated.getReportingTotal().getOriginalAmount());
    assertEquals("USD", updated.getReportingTotal().getOriginalCurrency());
    assertEquals(BigDecimal.ZERO, updated.getReportingTotal().getConvertedAmount());
    assertEquals("GBP", updated.getReportingTotal().getConvertedCurrency());
    assertEquals(BigDecimal.ONE, updated.getReportingTotal().getExchangeRate());
    assertEquals(docDate, updated.getReportingTotal().getRateDate());
    assertEquals("New Customer Ltd", response.getCustomerName());
    verify(exchangeRateService, never()).convert(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Should update customer and currency on line-less evaluation quotes")
  void shouldUpdateCustomerAndCurrencyOnLineLessEvaluationQuotes() {
    UUID newCustomerId = UUID.randomUUID();
    quote.setStatus(QuoteStatus.EVALUATION);

    UpdateQuoteRequest req = new UpdateQuoteRequest();
    req.setCustomerId(newCustomerId);
    req.setCurrency("EUR");

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(tenantReportingCurrencyPort.getReportingCurrency(tenantId)).thenReturn("EUR");
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));

    Quote updated = quoteService.updateQuoteHeader(quoteId, req);

    assertEquals(newCustomerId, updated.getCustomerId());
    assertEquals("EUR", updated.getCurrency());
    assertEquals(QuoteStatus.EVALUATION, updated.getStatus());
    assertEquals(0, updated.getTotalAmount().getAmount().compareTo(BigDecimal.ZERO));
  }

  @Test
  @DisplayName("Should reject customer and currency changes after quote has lines")
  void shouldRejectCustomerAndCurrencyChangesAfterQuoteHasLines() {
    UUID originalCustomerId = quote.getCustomerId();
    quote.addLine(quoteLine("GBP", "10.00", "2.000"));

    UpdateQuoteRequest req = new UpdateQuoteRequest();
    req.setCustomerId(UUID.randomUUID());
    req.setCurrency("USD");

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class, () -> quoteService.updateQuoteHeader(quoteId, req));

    assertEquals("SALES_011_QUOTE_DRAFT_IDENTITY_LOCKED", ex.getErrorCode());
    assertEquals(409, ex.getHttpStatus());
    assertEquals(originalCustomerId, quote.getCustomerId());
    assertEquals("GBP", quote.getCurrency());
    verify(quoteRepository, never()).save(any(Quote.class));
  }

  @Test
  @DisplayName("Should reject customer and currency changes for locked quote statuses")
  void shouldRejectCustomerAndCurrencyChangesForLockedQuoteStatuses() {
    UpdateQuoteRequest req = new UpdateQuoteRequest();
    req.setCustomerId(UUID.randomUUID());
    req.setCurrency("USD");

    for (QuoteStatus status :
        List.of(
            QuoteStatus.PENDING_APPROVAL,
            QuoteStatus.APPROVED,
            QuoteStatus.REJECTED,
            QuoteStatus.CONVERTED,
            QuoteStatus.EXPIRED,
            QuoteStatus.SUPERSEDED)) {
      Quote lockedQuote = quote("GBP");
      lockedQuote.setStatus(status);

      when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
          .thenReturn(Optional.of(lockedQuote));

      SalesDomainException ex =
          assertThrows(
              SalesDomainException.class, () -> quoteService.updateQuoteHeader(quoteId, req));

      assertEquals("SALES_011_QUOTE_DRAFT_IDENTITY_LOCKED", ex.getErrorCode());
      assertEquals(409, ex.getHttpStatus());
      assertEquals(customerId, lockedQuote.getCustomerId());
      assertEquals("GBP", lockedQuote.getCurrency());
    }

    verify(quoteRepository, never()).save(any(Quote.class));
  }

  @Test
  @DisplayName("Should preserve tenant isolation when updating customer and currency")
  void shouldPreserveTenantIsolationWhenUpdatingCustomerAndCurrency() {
    UpdateQuoteRequest req = new UpdateQuoteRequest();
    req.setCustomerId(UUID.randomUUID());
    req.setCurrency("USD");

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.empty());

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class, () -> quoteService.updateQuoteHeader(quoteId, req));

    assertEquals("SALES_002_QUOTE_NOT_FOUND", ex.getErrorCode());
    verify(quoteRepository).findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId);
    verify(quoteRepository, never()).save(any(Quote.class));
    verify(tradingPartnerResolver, never()).resolveDisplayNames(eq(tenantId), any());
    verify(tenantReportingCurrencyPort, never()).getReportingCurrency(any());
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
        SalesDomainException.class, () -> quoteService.sendQuote(quoteId, contactId, false));

    verify(quoteRepository, times(4)).findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId);
    verify(quoteRepository, never()).save(any(Quote.class));
    verify(quoteApprovalService, never())
        .generateTokenForQuote(any(), any(QuoteApprovalChannel.class), any(), any());
  }

  @Test
  @DisplayName("Approver send should submit clean draft quote and mint email token")
  void approverSendShouldSubmitCleanDraftQuoteAndMintEmailToken() {
    quote.addLine(quoteLine("GBP", "10.00", "2.000"));
    QuoteApprovalToken approvalToken = quoteApprovalToken("buyer@example.com");

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(partnerContactService.requireActiveContact(tenantId, contactId))
        .thenReturn(partnerContact(contactId, customerId, "buyer@example.com"));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quoteApprovalService.generateTokenForQuote(
            quoteId, QuoteApprovalChannel.EMAIL, "buyer@example.com", contactId))
        .thenReturn(approvalToken);

    SendQuoteResult result = quoteService.sendQuote(quoteId, contactId, true);

    assertEquals(approvalToken, result.approvalToken());
    assertNull(result.sendRequest());
    assertEquals(QuoteStatus.APPROVED, quote.getStatus());
    verify(quoteApprovalService)
        .generateTokenForQuote(quoteId, QuoteApprovalChannel.EMAIL, "buyer@example.com", contactId);
  }

  @Test
  @DisplayName("Non-approver send should create pending send request and not mint token")
  void nonApproverSendShouldCreatePendingSendRequestAndNotMintToken() {
    quote.addLine(quoteLine("GBP", "10.00", "2.000"));

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(partnerContactService.requireActiveContact(tenantId, contactId))
        .thenReturn(partnerContact(contactId, customerId, "buyer@example.com"));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quoteSendRequestRepository.findPendingByTenantIdAndQuoteId(tenantId, quoteId))
        .thenReturn(Optional.empty());
    when(quoteSendRequestRepository.save(any(QuoteSendRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SendQuoteResult result = quoteService.sendQuote(quoteId, contactId, false);

    assertNull(result.approvalToken());
    assertNotNull(result.sendRequest());
    assertEquals(QuoteSendRequestStatus.PENDING, result.sendRequest().getStatus());
    assertEquals(contactId, result.sendRequest().getContactId());
    assertEquals(userId, result.sendRequest().getRequestedBy());
    assertEquals(QuoteStatus.APPROVED, quote.getStatus());
    verify(quoteApprovalService, never())
        .generateTokenForQuote(any(), any(QuoteApprovalChannel.class), any(), any());
    verify(eventPublisher).publishEvent(any(QuoteSendRequestedEvent.class));
  }

  @Test
  @DisplayName("Should reject quote send when contact belongs to another customer")
  void shouldRejectQuoteSendWhenContactBelongsToAnotherCustomer() {
    UUID otherCustomerId = UUID.randomUUID();
    quote.setStatus(QuoteStatus.APPROVED);
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(partnerContactService.requireActiveContact(tenantId, contactId))
        .thenReturn(partnerContact(contactId, otherCustomerId, "buyer@example.com"));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class, () -> quoteService.sendQuote(quoteId, contactId, true));

    assertEquals("SALES_010_INVALID_QUOTE_RECIPIENT_CONTACT", ex.getErrorCode());
    assertEquals(400, ex.getHttpStatus());
    verify(quoteApprovalService, never())
        .generateTokenForQuote(any(), any(QuoteApprovalChannel.class), any(), any());
  }

  @Test
  @DisplayName("Non-approver manager approval quote should create pending send request")
  void nonApproverManagerApprovalQuoteShouldCreatePendingSendRequest() {
    QuoteLine line = quoteLine("GBP", "10.00", "2.000");
    line.setPriceZone(QuotePriceZone.MANAGER_APPROVAL);
    quote.addLine(line);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(partnerContactService.requireActiveContact(tenantId, contactId))
        .thenReturn(partnerContact(contactId, customerId, "buyer@example.com"));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quoteSendRequestRepository.findPendingByTenantIdAndQuoteId(tenantId, quoteId))
        .thenReturn(Optional.empty());
    when(quoteSendRequestRepository.save(any(QuoteSendRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    SendQuoteResult result = quoteService.sendQuote(quoteId, contactId, false);

    assertNull(result.approvalToken());
    assertNotNull(result.sendRequest());
    assertEquals(QuoteStatus.PENDING_APPROVAL, quote.getStatus());
    verify(quoteApprovalService, never())
        .generateTokenForQuote(any(), any(QuoteApprovalChannel.class), any(), any());
  }

  @Test
  @DisplayName("Approver send should approve manager-zone quote and mint email token")
  void approverSendShouldApproveManagerZoneQuoteAndMintToken() {
    QuoteLine line = quoteLine("GBP", "10.00", "2.000");
    line.setPriceZone(QuotePriceZone.MANAGER_APPROVAL);
    quote.addLine(line);
    QuoteApprovalToken approvalToken = quoteApprovalToken("buyer@example.com");

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(partnerContactService.requireActiveContact(tenantId, contactId))
        .thenReturn(partnerContact(contactId, customerId, "buyer@example.com"));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quoteApprovalService.generateTokenForQuote(
            quoteId, QuoteApprovalChannel.EMAIL, "buyer@example.com", contactId))
        .thenReturn(approvalToken);

    SendQuoteResult result = quoteService.sendQuote(quoteId, contactId, true);

    assertEquals(approvalToken, result.approvalToken());
    assertEquals(QuoteStatus.APPROVED, quote.getStatus());
    verify(quoteApprovalService)
        .generateTokenForQuote(quoteId, QuoteApprovalChannel.EMAIL, "buyer@example.com", contactId);
  }

  @Test
  @DisplayName("Second open send request should return conflict")
  void secondOpenSendRequestShouldReturnConflict() {
    quote.setStatus(QuoteStatus.APPROVED);
    QuoteSendRequest existing = quoteSendRequest();

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(partnerContactService.requireActiveContact(tenantId, contactId))
        .thenReturn(partnerContact(contactId, customerId, "buyer@example.com"));
    when(quoteSendRequestRepository.findPendingByTenantIdAndQuoteId(tenantId, quoteId))
        .thenReturn(Optional.of(existing));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class, () -> quoteService.sendQuote(quoteId, contactId, false));

    assertEquals("SALES_012_QUOTE_SEND_REQUEST_ALREADY_PENDING", ex.getErrorCode());
    verify(quoteApprovalService, never())
        .generateTokenForQuote(any(), any(QuoteApprovalChannel.class), any(), any());
    verify(quoteSendRequestRepository, never()).save(any(QuoteSendRequest.class));
  }

  @Test
  @DisplayName("Approve send request should approve pending quote and mint email token")
  void approveSendRequestShouldApprovePendingQuoteAndMintEmailToken() {
    quote.setStatus(QuoteStatus.PENDING_APPROVAL);
    QuoteSendRequest request = quoteSendRequest();
    QuoteApprovalToken approvalToken = quoteApprovalToken("buyer@example.com");

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteSendRequestRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, request.getId()))
        .thenReturn(Optional.of(request));
    when(partnerContactService.requireActiveContact(tenantId, contactId))
        .thenReturn(partnerContact(contactId, customerId, "buyer@example.com"));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quoteSendRequestRepository.save(any(QuoteSendRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(quoteApprovalService.generateTokenForQuote(
            quoteId, QuoteApprovalChannel.EMAIL, "buyer@example.com", contactId))
        .thenReturn(approvalToken);

    SendQuoteResult result = quoteService.approveSendRequest(quoteId, request.getId());

    assertEquals(approvalToken, result.approvalToken());
    assertEquals(QuoteSendRequestStatus.APPROVED, result.sendRequest().getStatus());
    assertEquals(userId, result.sendRequest().getDecidedBy());
    assertEquals(QuoteStatus.APPROVED, quote.getStatus());
  }

  @Test
  @DisplayName("Reject send request should return quote to evaluation and notify requester")
  void rejectSendRequestShouldReturnQuoteToEvaluationAndNotifyRequester() {
    quote.setStatus(QuoteStatus.PENDING_APPROVAL);
    QuoteSendRequest request = quoteSendRequest();

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(quoteSendRequestRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, request.getId()))
        .thenReturn(Optional.of(request));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    when(quoteSendRequestRepository.save(any(QuoteSendRequest.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    QuoteSendRequest rejected =
        quoteService.rejectSendRequest(quoteId, request.getId(), "Margin too low");

    assertEquals(QuoteStatus.EVALUATION, quote.getStatus());
    assertEquals(QuoteSendRequestStatus.REJECTED, rejected.getStatus());
    assertEquals("Margin too low", rejected.getDecisionNote());
    verify(eventPublisher).publishEvent(any(QuoteSendRequestRejectedEvent.class));
  }

  @Test
  @DisplayName("Should return needs approval for blocked quote and not mint token")
  void shouldReturnNeedsApprovalForBlockedQuoteAndNotMintToken() {
    QuoteLine line = quoteLine("GBP", "10.00", "2.000");
    line.setPriceZone(QuotePriceZone.BLOCKED);
    quote.addLine(line);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(partnerContactService.requireActiveContact(tenantId, contactId))
        .thenReturn(partnerContact(contactId, customerId, "buyer@example.com"));

    SalesDomainException ex =
        assertThrows(
            SalesDomainException.class, () -> quoteService.sendQuote(quoteId, contactId, false));

    assertEquals("SALES_006_QUOTE_NEEDS_INTERNAL_APPROVAL", ex.getErrorCode());
    assertEquals(QuoteStatus.DRAFT, quote.getStatus());
    verify(quoteRepository, never()).save(any(Quote.class));
    verify(quoteApprovalService, never())
        .generateTokenForQuote(any(), any(QuoteApprovalChannel.class), any(), any());
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
        "Combed cotton fabric",
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

  private QuoteSendRequest quoteSendRequest() {
    QuoteSendRequest request =
        QuoteSendRequest.create(
            tenantId, quoteId, contactId, QuoteApprovalChannel.EMAIL, userId, Instant.now());
    request.setId(UUID.randomUUID());
    return request;
  }

  private PartnerContact partnerContact(UUID contactId, UUID partnerId, String email) {
    TradingPartnerRegistry registry =
        TradingPartnerRegistry.builder().id(UUID.randomUUID()).officialName("Acme").build();
    TradingPartner partner =
        TradingPartner.builder()
            .registry(registry)
            .partnerType(PartnerType.CUSTOMER)
            .customName("Acme")
            .build();
    partner.setId(partnerId);
    partner.setTenantId(tenantId);

    PartnerContact contact =
        PartnerContact.builder()
            .partner(partner)
            .name("Buyer")
            .email(email)
            .role(PartnerContactRole.BUYER)
            .primaryContact(true)
            .build();
    contact.setId(contactId);
    contact.setTenantId(tenantId);
    return contact;
  }
}
