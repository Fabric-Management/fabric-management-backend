package com.fabricmanagement.sales.quote.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.testsupport.PostgresImage;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
class QuoteObjectScopeIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("quote_scope_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private QuoteRepository quotes;
  @Autowired private CacheManager cacheManager;

  @MockitoBean private PermissionEvaluator permissionEvaluator;
  @MockitoBean private UserQueryService userQueryService;
  @MockitoBean private TradingPartnerResolver tradingPartnerResolver;

  private final Map<UUID, DataScope> readScopes = new HashMap<>();
  private final Map<UUID, List<String>> departments = new HashMap<>();
  private final UUID tenantId = UUID.randomUUID();
  private final UUID otherTenantId = UUID.randomUUID();
  private final UUID creatorId = UUID.randomUUID();
  private final UUID assigneeId = UUID.randomUUID();
  private final UUID salesColleagueId = UUID.randomUUID();
  private final UUID strangerId = UUID.randomUUID();
  private Quote creatorQuote;
  private Quote assignedQuote;
  private Quote outsiderQuote;

  @BeforeEach
  void setUp() {
    reset(permissionEvaluator, userQueryService, tradingPartnerResolver);
    readScopes.clear();
    departments.clear();
    departments.put(creatorId, List.of("SALES"));
    departments.put(assigneeId, List.of("SALES"));
    departments.put(salesColleagueId, List.of("SALES"));
    departments.put(strangerId, List.of("FINANCE"));
    stubIdentityAndPermissions();
    when(tradingPartnerResolver.resolveDisplayNames(any(), any())).thenReturn(Map.of());
    when(tradingPartnerResolver.findCustomerIdsByNameContains(any(), any())).thenReturn(List.of());

    creatorQuote =
        persistQuote("QT-CREATOR-" + UUID.randomUUID(), creatorId, strangerId, QuoteStatus.DRAFT);
    assignedQuote =
        persistQuote("QT-ASSIGNED-" + UUID.randomUUID(), strangerId, assigneeId, QuoteStatus.DRAFT);
    outsiderQuote =
        persistQuote(
            "QT-OUTSIDER-" + UUID.randomUUID(), strangerId, strangerId, QuoteStatus.APPROVED);
    persistQuote(
        otherTenantId, "QT-OTHER-" + UUID.randomUUID(), strangerId, strangerId, QuoteStatus.DRAFT);
    TenantContext.clear();
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void ownScopeUsesCreatorOrAssigneeForListDetailSearchCountsAndPaging() throws Exception {
    grantRead(creatorId, DataScope.OWN);
    performAs(creatorId, get("/api/v1/sales/quotes?page=0&size=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(creatorQuote.getId().toString()));
    performAs(creatorId, get("/api/v1/sales/quotes/{id}", creatorQuote.getId()))
        .andExpect(status().isOk());
    performAs(
            creatorId,
            get("/api/v1/sales/quotes?q={query}", creatorQuote.getQuoteNumber().substring(3)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
    performAs(creatorId, get("/api/v1/sales/quotes/status-counts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.draft").value(1))
        .andExpect(jsonPath("$.data.approved").value(0));

    when(tradingPartnerResolver.findCustomerIdsByNameContains(tenantId, "Acme"))
        .thenReturn(List.of(creatorQuote.getCustomerId()));
    performAs(creatorId, get("/api/v1/sales/quotes?q=Acme"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(creatorQuote.getId().toString()));

    persistQuote("QT-PG-" + UUID.randomUUID(), creatorId, strangerId, QuoteStatus.DRAFT);
    performAs(creatorId, get("/api/v1/sales/quotes?page=0&size=1&sort=quoteNumber,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(1));
    performAs(creatorId, get("/api/v1/sales/quotes?page=1&size=1&sort=quoteNumber,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(1));
    performAs(creatorId, get("/api/v1/sales/quotes?page=2&size=1&sort=quoteNumber,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(0));

    grantRead(assigneeId, DataScope.OWN);
    performAs(assigneeId, get("/api/v1/sales/quotes/{id}", assignedQuote.getId()))
        .andExpect(status().isOk());
    performAs(assigneeId, get("/api/v1/sales/quotes?status=DRAFT&page=0&size=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void departmentAndOrganizationScopesDoNotCrossTenantOrDuplicateDualPrincipalQuotes()
      throws Exception {
    grantRead(salesColleagueId, DataScope.DEPARTMENT);
    when(userQueryService.findActiveUserIdsByDepartmentCodes(tenantId, Set.of("SALES")))
        .thenReturn(Set.of(creatorId, assigneeId, salesColleagueId));
    clearPermissionCache();
    performAs(salesColleagueId, get("/api/v1/sales/quotes?page=0&size=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));

    persistQuote("QT-DUAL-" + UUID.randomUUID(), creatorId, creatorId, QuoteStatus.DRAFT);
    grantRead(creatorId, DataScope.OWN);
    performAs(creatorId, get("/api/v1/sales/quotes?page=0&size=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2));

    grantRead(strangerId, DataScope.ORGANIZATION);
    performAs(strangerId, get("/api/v1/sales/quotes?page=0&size=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(4));
  }

  @Test
  void outOfScopeDetailHasTheMissingQuoteContract() throws Exception {
    grantRead(creatorId, DataScope.OWN);

    performAs(creatorId, get("/api/v1/sales/quotes/{id}", outsiderQuote.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    performAs(creatorId, get("/api/v1/sales/quotes/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
  }

  private ResultActions performAs(UUID userId, MockHttpServletRequestBuilder request)
      throws Exception {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
    AuthenticatedUserContext context =
        new AuthenticatedUserContext(userId, "WORKER", departments.get(userId), null, tenantId);
    var token = UsernamePasswordAuthenticationToken.authenticated(context, "n/a", List.of());
    token.setDetails(context);
    return mockMvc.perform(request.with(authentication(token)));
  }

  private void stubIdentityAndPermissions() {
    when(userQueryService.findPermissionIdentity(any(), any()))
        .thenAnswer(
            invocation -> {
              UUID requestedTenant = invocation.getArgument(0);
              UUID userId = invocation.getArgument(1);
              if (!tenantId.equals(requestedTenant) || !departments.containsKey(userId)) {
                return Optional.empty();
              }
              return Optional.of(new PermissionIdentity("WORKER", departments.get(userId)));
            });
    when(permissionEvaluator.evaluate(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              UUID userId = invocation.getArgument(3);
              DataScope scope = readScopes.get(userId);
              return scope == null
                  ? new PermissionResult(Map.of(), false)
                  : new PermissionResult(Map.of("sales", Map.of("read", scope)), false);
            });
  }

  private void grantRead(UUID userId, DataScope scope) {
    readScopes.put(userId, scope);
    clearPermissionCache();
  }

  private void clearPermissionCache() {
    Cache cache = cacheManager.getCache("permissions");
    if (cache != null) {
      cache.clear();
    }
  }

  private Quote persistQuote(
      String quoteNumber, UUID createdBy, UUID assignedToId, QuoteStatus status) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(createdBy);
    Quote quote = new Quote();
    quote.setTenantId(tenantId);
    quote.setCreatedBy(createdBy);
    quote.setQuoteNumber(quoteNumber);
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

  private Quote persistQuote(
      UUID targetTenantId,
      String quoteNumber,
      UUID createdBy,
      UUID assignedToId,
      QuoteStatus status) {
    TenantContext.setCurrentTenantId(targetTenantId);
    TenantContext.setCurrentUserId(createdBy);
    Quote quote = new Quote();
    quote.setTenantId(targetTenantId);
    quote.setCreatedBy(createdBy);
    quote.setQuoteNumber(quoteNumber);
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
}
