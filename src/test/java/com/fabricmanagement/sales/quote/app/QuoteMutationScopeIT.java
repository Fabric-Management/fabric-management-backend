package com.fabricmanagement.sales.quote.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.production.core.batch.infra.repository.BatchLotQuantityIntentRepository;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import com.fabricmanagement.sales.quote.domain.QuoteSendRequest;
import com.fabricmanagement.sales.quote.domain.QuoteSendRequestStatus;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.infra.repository.QuoteApprovalTokenRepository;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.quote.infra.repository.QuoteSendRequestRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class QuoteMutationScopeIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private QuoteRepository quotes;
  @Autowired private QuoteApprovalTokenRepository approvalTokens;
  @Autowired private BatchLotQuantityIntentRepository lotIntents;
  @Autowired private CacheManager cacheManager;

  @MockitoBean private PermissionEvaluator permissionEvaluator;
  @MockitoBean private UserQueryService userQueryService;
  @MockitoBean private QuoteSendRequestRepository sendRequests;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();
  private final Map<String, DataScope> actions = new HashMap<>();
  private Quote inaccessibleQuote;

  @BeforeEach
  void setUp() {
    reset(permissionEvaluator, userQueryService, sendRequests);
    actions.clear();
    when(userQueryService.findPermissionIdentity(any(), any()))
        .thenAnswer(
            invocation -> {
              UUID requestedTenant = invocation.getArgument(0);
              UUID requestedUser = invocation.getArgument(1);
              return tenantId.equals(requestedTenant)
                      && (actorId.equals(requestedUser) || ownerId.equals(requestedUser))
                  ? Optional.of(new PermissionIdentity("WORKER", List.of("SALES")))
                  : Optional.empty();
            });
    when(permissionEvaluator.evaluate(any(), any(), any(), any()))
        .thenAnswer(
            invocation ->
                actions.isEmpty()
                    ? new PermissionResult(Map.of(), false)
                    : new PermissionResult(Map.of("sales", Map.copyOf(actions)), false));
    inaccessibleQuote = persistQuote(ownerId, ownerId, QuoteStatus.DRAFT);
    TenantContext.clear();
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void everyExistingQuoteMutationDeniesOutOfScopeBeforeChangingTheQuote() throws Exception {
    grant("write", DataScope.OWN);
    UUID quoteId = inaccessibleQuote.getId();
    UUID childId = UUID.randomUUID();
    Map<String, Object> lineBody =
        Map.of(
            "productId", UUID.randomUUID(),
            "requestedQty", "10.000",
            "unit", "M",
            "offeredPrice", "3.2500");
    Map<String, Object> updateLineBody =
        Map.of("requestedQty", "10.000", "unit", "M", "offeredPrice", "3.2500");

    assertDeniedAndUnchanged(
        post("/api/v1/sales/quotes/{id}/lines", quoteId).content(json(lineBody)));
    assertDeniedAndUnchanged(
        patch("/api/v1/sales/quotes/{id}", quoteId).content(json(Map.of("notes", "blocked"))));
    assertDeniedAndUnchanged(
        patch("/api/v1/sales/quotes/{id}/lines/{lineId}", quoteId, childId)
            .content(json(updateLineBody)));
    assertDeniedAndUnchanged(delete("/api/v1/sales/quotes/{id}/lines/{lineId}", quoteId, childId));
    assertDeniedAndUnchanged(post("/api/v1/sales/quotes/{id}/submit", quoteId));
    assertDeniedAndUnchanged(
        post("/api/v1/sales/quotes/{id}/send", quoteId)
            .content(json(Map.of("contactId", UUID.randomUUID()))));
    assertDeniedAndUnchanged(post("/api/v1/sales/quotes/{id}/revise", quoteId));
    assertDeniedAndUnchanged(
        post("/api/v1/sales/quotes/{id}/tokens", quoteId)
            .content(json(Map.of("channel", "EMAIL", "sentTo", "scope@example.test"))));

    grant("approve", DataScope.ORGANIZATION);
    assertDeniedAndUnchanged(
        post("/api/v1/sales/quotes/{id}/send", quoteId)
            .content(json(Map.of("contactId", UUID.randomUUID()))));
    assertDeniedAndUnchanged(
        post("/api/v1/sales/quotes/{id}/send-requests/{requestId}/approve", quoteId, childId));
    assertDeniedAndUnchanged(
        post("/api/v1/sales/quotes/{id}/send-requests/{requestId}/reject", quoteId, childId)
            .content(json(Map.of("decisionNote", "blocked"))));
  }

  @Test
  void approveActionWithoutWritePairIsRefusedForAllApprovalPaths() throws Exception {
    Quote ownQuote = persistQuote(actorId, actorId, QuoteStatus.APPROVED);
    grant("approve", DataScope.ORGANIZATION);
    UUID requestId = UUID.randomUUID();

    perform(
            post("/api/v1/sales/quotes/{id}/send", ownQuote.getId())
                .content(json(Map.of("contactId", UUID.randomUUID()))))
        .andExpect(status().isForbidden());
    perform(
            post(
                "/api/v1/sales/quotes/{id}/send-requests/{requestId}/approve",
                ownQuote.getId(),
                requestId))
        .andExpect(status().isForbidden());
    perform(
            post(
                    "/api/v1/sales/quotes/{id}/send-requests/{requestId}/reject",
                    ownQuote.getId(),
                    requestId)
                .content(json(Map.of("decisionNote", "No write scope"))))
        .andExpect(status().isForbidden());

    assertThat(reload(ownQuote.getId()).getStatus()).isEqualTo(QuoteStatus.APPROVED);
  }

  @Test
  void childIdsRemainBoundToTheAuthorizedParentQuote() throws Exception {
    grant("write", DataScope.OWN);
    grant("approve", DataScope.ORGANIZATION);
    Quote target = persistQuote(actorId, actorId, QuoteStatus.DRAFT);
    Quote other = persistQuote(actorId, actorId, QuoteStatus.DRAFT);
    QuoteLine otherLine = addLine(other);
    UUID requestId = UUID.randomUUID();
    QuoteSendRequest otherRequest =
        QuoteSendRequest.create(
            tenantId,
            other.getId(),
            UUID.randomUUID(),
            QuoteApprovalChannel.EMAIL,
            actorId,
            Instant.now());
    otherRequest.setId(requestId);
    when(sendRequests.findByTenantIdAndIdAndIsActiveTrue(tenantId, requestId))
        .thenReturn(Optional.of(otherRequest));

    perform(
            patch("/api/v1/sales/quotes/{id}/lines/{lineId}", target.getId(), otherLine.getId())
                .content(
                    json(
                        Map.of(
                            "requestedQty", "10.000",
                            "unit", "M",
                            "offeredPrice", "3.2500"))))
        .andExpect(status().isNotFound());
    perform(
            post(
                "/api/v1/sales/quotes/{id}/send-requests/{requestId}/approve",
                target.getId(),
                requestId))
        .andExpect(status().isNotFound());

    assertThat(reload(target.getId()).getStatus()).isEqualTo(QuoteStatus.DRAFT);
    assertThat(otherRequest.getStatus()).isEqualTo(QuoteSendRequestStatus.PENDING);
    org.mockito.Mockito.verify(sendRequests, org.mockito.Mockito.never()).save(any());
  }

  @Test
  void revisionBelongsToTheReviserAndDoesNotInheritTheOriginalCreator() throws Exception {
    UUID assigneeId = UUID.randomUUID();
    Quote original = persistQuote(ownerId, assigneeId, QuoteStatus.APPROVED);
    grant("write", DataScope.ORGANIZATION);

    perform(post("/api/v1/sales/quotes/{id}/revise", original.getId()))
        .andExpect(status().isCreated());

    Quote revision =
        quotes.findAll().stream()
            .filter(candidate -> original.getId().equals(candidate.getParentQuoteId()))
            .findFirst()
            .orElseThrow();
    assertThat(revision.getCreatedBy()).isEqualTo(actorId);
    assertThat(revision.getAssignedToId()).isEqualTo(assigneeId);

    grant("read", DataScope.OWN);
    performAs(ownerId, get("/api/v1/sales/quotes/{id}", revision.getId()))
        .andExpect(status().isNotFound());
  }

  private void assertDeniedAndUnchanged(MockHttpServletRequestBuilder request) throws Exception {
    Quote before = reload(inaccessibleQuote.getId());
    Snapshot snapshot =
        new Snapshot(
            before.getStatus(),
            before.getVersion(),
            before.getLines().size(),
            approvalTokens.count(),
            lotIntents.count());

    perform(request).andExpect(status().isForbidden());

    Quote after = reload(inaccessibleQuote.getId());
    assertThat(
            new Snapshot(
                after.getStatus(),
                after.getVersion(),
                after.getLines().size(),
                approvalTokens.count(),
                lotIntents.count()))
        .isEqualTo(snapshot);
  }

  private org.springframework.test.web.servlet.ResultActions perform(
      MockHttpServletRequestBuilder request) throws Exception {
    return performAs(actorId, request);
  }

  private org.springframework.test.web.servlet.ResultActions performAs(
      UUID userId, MockHttpServletRequestBuilder request) throws Exception {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
    AuthenticatedUserContext context =
        new AuthenticatedUserContext(userId, "WORKER", List.of("SALES"), null, tenantId);
    var token = UsernamePasswordAuthenticationToken.authenticated(context, "n/a", List.of());
    token.setDetails(context);
    return mockMvc.perform(
        request.with(authentication(token)).with(csrf()).contentType(MediaType.APPLICATION_JSON));
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  private void grant(String action, DataScope scope) {
    actions.put(action, scope);
    clearPermissionCache();
  }

  private void clearPermissionCache() {
    Cache cache = cacheManager.getCache("permissions");
    if (cache != null) {
      cache.clear();
    }
  }

  private Quote persistQuote(UUID createdBy, UUID assignedToId, QuoteStatus status) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(createdBy);
    Quote quote = new Quote();
    quote.setTenantId(tenantId);
    quote.setCreatedBy(createdBy);
    quote.setQuoteNumber("QT-MUT-" + UUID.randomUUID());
    quote.setCustomerId(UUID.randomUUID());
    quote.setAssignedToId(assignedToId);
    quote.setOwnerResolutionReason(OwnerResolutionReason.EXPLICIT_OVERRIDE);
    quote.setModuleType("FABRIC");
    quote.setStatus(status);
    quote.setCurrency("GBP");
    quote.setValidUntil(LocalDate.now().plusDays(30));
    quote.setPaymentTerms("NET_30");
    return quotes.saveAndFlush(quote);
  }

  private QuoteLine addLine(Quote quote) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(actorId);
    QuoteLine line = new QuoteLine();
    line.setTenantId(tenantId);
    line.setCreatedBy(actorId);
    line.setRequestedQty(new BigDecimal("10.000"));
    line.setUnit("M");
    line.setListPrice(new BigDecimal("4.0000"));
    line.setOfferedPrice(new BigDecimal("3.5000"));
    line.setCurrency("GBP");
    line.setDiscountRate(new BigDecimal("0.1250"));
    line.setProfitMargin(new BigDecimal("0.1000"));
    line.setPriceZone(QuotePriceZone.FREE);
    quote.addLine(line);
    return quotes.saveAndFlush(quote).getLines().getFirst();
  }

  private Quote reload(UUID quoteId) {
    TenantContext.setCurrentTenantId(tenantId);
    return quotes.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId).orElseThrow();
  }

  private record Snapshot(
      QuoteStatus status, Long version, int lineCount, long tokenCount, long intentCount) {}
}
