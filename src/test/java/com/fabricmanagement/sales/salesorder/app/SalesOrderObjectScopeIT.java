package com.fabricmanagement.sales.salesorder.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserDepartmentRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fabricmanagement.testsupport.PostgresImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
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
class SalesOrderObjectScopeIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("sales_scope_test")
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
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private DepartmentRepository departmentRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserDepartmentRepository userDepartmentRepository;
  @Autowired private TradingPartnerRegistryRepository tradingPartnerRegistryRepository;
  @Autowired private TradingPartnerRepository tradingPartnerRepository;
  @Autowired private SalesOrderRepository salesOrderRepository;
  @Autowired private CacheManager cacheManager;

  @MockitoBean private PermissionEvaluator permissionEvaluator;

  private final Map<UUID, DataScope> readScopes = new HashMap<>();
  private final Map<UUID, DataScope> writeScopes = new HashMap<>();

  private TestUser creatorA;
  private TestUser userB;
  private TestUser outsiderC;
  private TestUser tenantBUser;
  private UUID tenantAId;
  private UUID tenantBId;
  private UUID partnerAId;
  private SalesOrder orderA;
  private SalesOrder orderBOne;
  private SalesOrder orderBTwo;
  private SalesOrder orderC;
  private SalesOrder tenantBOrder;

  @BeforeEach
  void setUp() {
    reset(permissionEvaluator);
    readScopes.clear();
    writeScopes.clear();
    stubPermissionEvaluation();

    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Tenant tenantA = tenantRepository.save(Tenant.create("Scope A " + suffix, "SCA-" + suffix));
    Tenant tenantB = tenantRepository.save(Tenant.create("Scope B " + suffix, "SCB-" + suffix));
    tenantAId = tenantA.getId();
    tenantBId = tenantB.getId();

    TenantFixture fixtureA = createTenantFixture(tenantAId, suffix + "A");
    creatorA = createUser(fixtureA, "Creator", "A", fixtureA.salesDepartment());
    userB = createUser(fixtureA, "User", "B", fixtureA.salesDepartment());
    outsiderC = createUser(fixtureA, "Outsider", "C", fixtureA.financeDepartment());
    partnerAId = createPartner(tenantAId, userB.id(), "Partner A " + suffix);

    orderA =
        createOrder(
            tenantAId,
            creatorA.id(),
            partnerAId,
            "SO-A-" + suffix,
            OrderStatus.DRAFT,
            LocalDate.now().minusDays(2));
    orderBOne =
        createOrder(
            tenantAId,
            userB.id(),
            partnerAId,
            "SO-B1-" + suffix,
            OrderStatus.DRAFT,
            LocalDate.now().minusDays(1));
    orderBTwo =
        createOrder(
            tenantAId,
            userB.id(),
            partnerAId,
            "SO-B2-" + suffix,
            OrderStatus.CONFIRMED,
            LocalDate.now().plusDays(5));
    orderC =
        createOrder(
            tenantAId,
            outsiderC.id(),
            partnerAId,
            "SO-C-" + suffix,
            OrderStatus.DRAFT,
            LocalDate.now().minusDays(3));

    TenantFixture fixtureB = createTenantFixture(tenantBId, suffix + "B");
    tenantBUser = createUser(fixtureB, "Tenant", "B", fixtureB.salesDepartment());
    UUID partnerBId = createPartner(tenantBId, tenantBUser.id(), "Partner B " + suffix);
    tenantBOrder =
        createOrder(
            tenantBId,
            tenantBUser.id(),
            partnerBId,
            "SO-TB-" + suffix,
            OrderStatus.DRAFT,
            LocalDate.now().minusDays(1));

    TenantContext.clear();
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void ownScopeHidesAnotherUsersOrderByIdAndNumberAsNotFound() throws Exception {
    grantRead(userB, DataScope.OWN);

    performAs(userB, get("/api/v1/sales/orders/{id}", orderA.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("Sales order not found: " + orderA.getId()));
    performAs(userB, get("/api/v1/sales/orders/number/{number}", orderA.getOrderNumber()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("Sales order not found: " + orderA.getOrderNumber()));

    UUID missingId = UUID.randomUUID();
    performAs(userB, get("/api/v1/sales/orders/{id}", missingId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("Sales order not found: " + missingId));
  }

  @Test
  void departmentGlobalAndTenantScopesAreApplied() throws Exception {
    grantRead(userB, DataScope.DEPARTMENT);
    performAs(userB, get("/api/v1/sales/orders/{id}", orderA.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(orderA.getId().toString()));
    performAs(userB, get("/api/v1/sales/orders/{id}", orderC.getId()))
        .andExpect(status().isNotFound());
    performAs(userB, get("/api/v1/sales/orders?page=0&size=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(3));

    grantRead(userB, DataScope.GLOBAL);
    performAs(userB, get("/api/v1/sales/orders/{id}", orderC.getId())).andExpect(status().isOk());
    performAs(userB, get("/api/v1/sales/orders?page=0&size=20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(4));
    performAs(userB, get("/api/v1/sales/orders/{id}", tenantBOrder.getId()))
        .andExpect(status().isNotFound());

    grantRead(tenantBUser, DataScope.GLOBAL);
    performAs(tenantBUser, get("/api/v1/sales/orders/{id}", tenantBOrder.getId()))
        .andExpect(status().isOk());
    performAs(tenantBUser, get("/api/v1/sales/orders/{id}", orderA.getId()))
        .andExpect(status().isNotFound());
  }

  @Test
  void pagingTotalsAndBoundariesUseOnlyInScopeOrders() throws Exception {
    grantRead(userB, DataScope.OWN);

    performAs(userB, get("/api/v1/sales/orders?page=0&size=1&sort=orderNumber,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(1));
    performAs(userB, get("/api/v1/sales/orders?page=1&size=1&sort=orderNumber,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(1));
    performAs(userB, get("/api/v1/sales/orders?page=2&size=1&sort=orderNumber,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(0));
  }

  @Test
  void filteredListsExcludeOutOfScopeOrders() throws Exception {
    grantRead(userB, DataScope.OWN);

    performAs(userB, get("/api/v1/sales/orders/open"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));
    performAs(userB, get("/api/v1/sales/orders/overdue"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(orderBOne.getId().toString()));
    performAs(userB, get("/api/v1/sales/orders/status/{status}", OrderStatus.DRAFT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(orderBOne.getId().toString()));
    performAs(userB, get("/api/v1/sales/orders/partner/{partnerId}", partnerAId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));
  }

  @Test
  void outOfScopeUpdateIsRejectedAndOrderRemainsUnchanged() throws Exception {
    grantWrite(userB, DataScope.OWN);
    String originalReference = orderA.getCustomerReference();
    Long originalVersion = orderA.getVersion();
    Map<String, Object> request =
        Map.of(
            "version",
            originalVersion,
            "customerReference",
            "changed",
            "orderDate",
            orderA.getOrderDate().toString(),
            "currency",
            "GBP",
            "lines",
            List.of());

    performAs(
            userB,
            put("/api/v1/sales/orders/{id}", orderA.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());

    TenantContext.setCurrentTenantId(tenantAId);
    SalesOrder unchanged =
        salesOrderRepository.findByTenantIdAndId(tenantAId, orderA.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(unchanged.getCustomerReference())
        .isEqualTo(originalReference);
    org.assertj.core.api.Assertions.assertThat(unchanged.getVersion()).isEqualTo(originalVersion);
  }

  private ResultActions performAs(TestUser user, MockHttpServletRequestBuilder request)
      throws Exception {
    TenantContext.setCurrentTenantId(user.tenantId());
    TenantContext.setCurrentUserId(user.id());
    AuthenticatedUserContext context =
        new AuthenticatedUserContext(
            user.id(), "WORKER", user.departmentCodes(), null, user.tenantId());
    var token = new UsernamePasswordAuthenticationToken(context, "n/a", List.of());
    token.setDetails(context);
    return mockMvc.perform(request.with(authentication(token)));
  }

  private void stubPermissionEvaluation() {
    when(permissionEvaluator.evaluate(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              UUID userId = invocation.getArgument(3);
              Map<String, DataScope> actions = new HashMap<>();
              if (readScopes.get(userId) != null) {
                actions.put("read", readScopes.get(userId));
              }
              if (writeScopes.get(userId) != null) {
                actions.put("write", writeScopes.get(userId));
              }
              return actions.isEmpty()
                  ? new PermissionResult(Map.of(), false)
                  : new PermissionResult(Map.of("sales", Map.copyOf(actions)), false);
            });
  }

  private void grantRead(TestUser user, DataScope scope) {
    readScopes.put(user.id(), scope);
    clearPermissionCache();
  }

  private void grantWrite(TestUser user, DataScope scope) {
    writeScopes.put(user.id(), scope);
    clearPermissionCache();
  }

  private void clearPermissionCache() {
    Cache cache = cacheManager.getCache("permissions");
    if (cache != null) {
      cache.clear();
    }
  }

  private TenantFixture createTenantFixture(UUID tenantId, String suffix) {
    TenantContext.setCurrentTenantId(tenantId);
    Organization organization =
        organizationRepository.save(
            Organization.create("Scope Org " + suffix, "TAX-" + suffix, OrganizationType.SPINNER));
    Role role = roleRepository.save(Role.create("Worker " + suffix, "WORKER", "Scope test"));
    Department sales =
        departmentRepository.save(
            Department.create(organization.getId(), "Sales", "SALES", "Sales"));
    Department finance =
        departmentRepository.save(
            Department.create(organization.getId(), "Finance", "FINANCE", "Finance"));
    return new TenantFixture(tenantId, organization, role, sales, finance);
  }

  private TestUser createUser(
      TenantFixture fixture, String firstName, String lastName, Department department) {
    TenantContext.setCurrentTenantId(fixture.tenantId());
    User user = User.create(firstName, lastName, fixture.organization().getId());
    user.setRole(fixture.role());
    user = userRepository.save(user);
    userDepartmentRepository.save(UserDepartment.create(user, department, true, user.getId()));
    return new TestUser(fixture.tenantId(), user.getId(), List.of(department.getDepartmentCode()));
  }

  private UUID createPartner(UUID tenantId, UUID userId, String name) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
    String suffix = UUID.randomUUID().toString();
    TradingPartnerRegistry registry = TradingPartnerRegistry.create(null, name, "GBR");
    registry.setUid("REG-" + suffix);
    registry = tradingPartnerRegistryRepository.save(registry);
    return tradingPartnerRepository
        .saveAndFlush(TradingPartner.create(registry, PartnerType.CUSTOMER, name))
        .getId();
  }

  private SalesOrder createOrder(
      UUID tenantId,
      UUID creatorId,
      UUID partnerId,
      String orderNumber,
      OrderStatus status,
      LocalDate promisedDeliveryDate) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(creatorId);
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(partnerId)
            .orderNumber(orderNumber)
            .status(status)
            .orderDate(LocalDate.now())
            .promisedDeliveryDate(promisedDeliveryDate)
            .totals(OrderTotals.zero("GBP"))
            .build();
    return salesOrderRepository.saveAndFlush(order);
  }

  private record TenantFixture(
      UUID tenantId,
      Organization organization,
      Role role,
      Department salesDepartment,
      Department financeDepartment) {}

  private record TestUser(UUID tenantId, UUID id, List<String> departmentCodes) {}
}
