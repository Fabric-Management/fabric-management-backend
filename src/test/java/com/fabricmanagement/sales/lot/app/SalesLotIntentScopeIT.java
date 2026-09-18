package com.fabricmanagement.sales.lot.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.production.core.batch.api.query.ProductionSalesLotQueryService;
import com.fabricmanagement.production.core.batch.api.query.ProductionSalesLotQueryService.LotColourReference;
import com.fabricmanagement.production.core.batch.api.query.ProductionSalesLotQueryService.LotQualityReference;
import com.fabricmanagement.production.core.batch.api.query.ProductionSalesLotQueryService.ProductionSalesLotIntentReference;
import com.fabricmanagement.production.core.batch.api.query.ProductionSalesLotQueryService.ProductionSalesLotReference;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SalesLotIntentScopeIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private QuoteRepository quotes;
  @Autowired private CacheManager cacheManager;

  @MockitoBean private ProductionSalesLotQueryService productionLots;
  @MockitoBean private PermissionEvaluator permissionEvaluator;
  @MockitoBean private UserQueryService userQueryService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userA = UUID.randomUUID();
  private final UUID userB = UUID.randomUUID();
  private final Map<UUID, DataScope> readScopes = new HashMap<>();
  private Quote quoteA;
  private Quote quoteB;

  @BeforeEach
  void setUp() {
    reset(productionLots, permissionEvaluator, userQueryService);
    readScopes.clear();
    when(userQueryService.findPermissionIdentity(any(), any()))
        .thenAnswer(
            invocation -> {
              UUID requestedTenant = invocation.getArgument(0);
              UUID userId = invocation.getArgument(1);
              return tenantId.equals(requestedTenant)
                      && (userA.equals(userId) || userB.equals(userId))
                  ? Optional.of(new PermissionIdentity("WORKER", List.of("SALES")))
                  : Optional.empty();
            });
    when(permissionEvaluator.evaluate(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              DataScope scope = readScopes.get((UUID) invocation.getArgument(3));
              return scope == null
                  ? new PermissionResult(Map.of(), false)
                  : new PermissionResult(Map.of("sales", Map.of("read", scope)), false);
            });

    quoteA = persistQuoteWithLine(userA, "QT-LOT-A-" + UUID.randomUUID());
    quoteB = persistQuoteWithLine(userB, "QT-LOT-B-" + UUID.randomUUID());
    when(productionLots.listSaleableLots(nullable(UUID.class)))
        .thenReturn(List.of(lotProjection()));
    TenantContext.clear();
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void ownReadersSeeOnlyTheirIntentWhileAllQuantitiesKeepTheTenantTotal() throws Exception {
    grantRead(userA, DataScope.OWN);
    performAs(userA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].softIntentQuantity").value(50.0))
        .andExpect(jsonPath("$.data[0].freeQuantity").value(50.0))
        .andExpect(jsonPath("$.data[0].intents.length()").value(1))
        .andExpect(jsonPath("$.data[0].intents[0].quoteId").value(quoteA.getId().toString()));

    grantRead(userB, DataScope.OWN);
    performAs(userB, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].softIntentQuantity").value(50.0))
        .andExpect(jsonPath("$.data[0].intents.length()").value(1))
        .andExpect(jsonPath("$.data[0].intents[0].quoteId").value(quoteB.getId().toString()));

    grantRead(userA, DataScope.ORGANIZATION);
    performAs(userA, null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].intents.length()").value(2));
  }

  @Test
  void outOfScopeQuoteLineBehavesLikeAnUnknownExclusion() throws Exception {
    grantRead(userA, DataScope.OWN);
    UUID hiddenLineId = quoteB.getLines().getFirst().getId();

    performAs(userA, hiddenLineId).andExpect(status().isOk());

    verify(productionLots).listSaleableLots(null);
    verify(productionLots, never()).listSaleableLots(hiddenLineId);
  }

  private org.springframework.test.web.servlet.ResultActions performAs(
      UUID userId, UUID quoteLineId) throws Exception {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
    AuthenticatedUserContext context =
        new AuthenticatedUserContext(userId, "WORKER", List.of("SALES"), null, tenantId);
    var token = UsernamePasswordAuthenticationToken.authenticated(context, "n/a", List.of());
    token.setDetails(context);
    var request = get("/api/v1/sales/lots").with(authentication(token));
    if (quoteLineId != null) {
      request.param("quoteLineId", quoteLineId.toString());
    }
    return mockMvc.perform(request);
  }

  private void grantRead(UUID userId, DataScope scope) {
    readScopes.put(userId, scope);
    Cache cache = cacheManager.getCache("permissions");
    if (cache != null) {
      cache.clear();
    }
  }

  private ProductionSalesLotReference lotProjection() {
    return new ProductionSalesLotReference(
        UUID.randomUUID(),
        "LOT-SCOPE-1",
        "AVAILABLE",
        true,
        "LENGTH",
        "M",
        new LotQualityReference(UUID.randomUUID(), "A", "Grade A", true, true),
        new LotColourReference(UUID.randomUUID(), "NAVY", "Navy", "#001F3F", null),
        new BigDecimal("100.000"),
        new BigDecimal("100.000"),
        new BigDecimal("50.000"),
        BigDecimal.ZERO,
        new BigDecimal("50.000"),
        false,
        List.of(
            new ProductionSalesLotIntentReference(
                quoteA.getId(),
                quoteA.getQuoteNumber(),
                "User A",
                new BigDecimal("20.000"),
                LocalDate.now().plusDays(5)),
            new ProductionSalesLotIntentReference(
                quoteB.getId(),
                quoteB.getQuoteNumber(),
                "User B",
                new BigDecimal("30.000"),
                LocalDate.now().plusDays(5))),
        List.of());
  }

  private Quote persistQuoteWithLine(UUID creatorId, String quoteNumber) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(creatorId);
    Quote quote = new Quote();
    quote.setTenantId(tenantId);
    quote.setCreatedBy(creatorId);
    quote.setQuoteNumber(quoteNumber);
    quote.setCustomerId(UUID.randomUUID());
    quote.setAssignedToId(creatorId);
    quote.setOwnerResolutionReason(OwnerResolutionReason.CREATOR_FALLBACK);
    quote.setModuleType("FABRIC");
    quote.setStatus(QuoteStatus.DRAFT);
    quote.setCurrency("GBP");
    quote.setValidUntil(LocalDate.now().plusDays(30));
    quote.setPaymentTerms("NET_30");

    QuoteLine line = new QuoteLine();
    line.setTenantId(tenantId);
    line.setCreatedBy(creatorId);
    line.setRequestedQty(new BigDecimal("10.000"));
    line.setUnit("M");
    line.setListPrice(new BigDecimal("4.0000"));
    line.setOfferedPrice(new BigDecimal("3.5000"));
    line.setCurrency("GBP");
    line.setDiscountRate(new BigDecimal("0.1250"));
    line.setProfitMargin(new BigDecimal("0.1000"));
    line.setPriceZone(QuotePriceZone.FREE);
    quote.addLine(line);
    return quotes.saveAndFlush(quote);
  }
}
