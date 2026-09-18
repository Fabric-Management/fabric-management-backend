package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.util.Money;
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
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.app.ruleengine.SalesOrderRuleEngine;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderCancelledEvent;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fabricmanagement.testsupport.PostgresImage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
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

/**
 * Real HTTP method security and access policy; permission resolution and downstream effects are
 * stubbed. Persisted order and line snapshots are the primary authorization evidence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
class SalesOrderMutationScopeIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("sales_mutation_scope_test")
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

  @Autowired private SalesOrderLineRepository lineRepository;
  @Autowired private SalesOrderService orderService;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private ApprovalPort approvalPort;
  @MockitoBean private SalesOrderRuleEngine ruleEngine;
  @MockitoBean private DomainEventPublisher eventPublisher;

  private final Map<String, DataScope> actions = new HashMap<>();
  private TestUser actor;
  private TestUser colleague;
  private TestUser outsider;
  private TestUser otherTenantUser;
  private UUID partnerId;
  private UUID otherPartnerId;

  @BeforeEach
  void setUp() {
    actions.clear();
    when(permissionEvaluator.evaluate(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> new PermissionResult(Map.of("sales", Map.copyOf(actions)), false));
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UUID tenantId =
        tenantRepository.save(Tenant.create("Mutation " + suffix, "SM-" + suffix)).getId();
    TenantFixture fixture = createTenantFixture(tenantId, suffix);
    actor = createUser(fixture, "Scope", "Actor", fixture.salesDepartment());
    colleague = createUser(fixture, "Scope", "Colleague", fixture.salesDepartment());
    outsider = createUser(fixture, "Scope", "Outsider", fixture.financeDepartment());
    partnerId = createPartner(tenantId, actor.id(), "Scope customer " + suffix);

    UUID otherTenant =
        tenantRepository.save(Tenant.create("Other " + suffix, "SX-" + suffix)).getId();
    TenantFixture other = createTenantFixture(otherTenant, suffix + "X");
    otherTenantUser = createUser(other, "Other", "User", other.salesDepartment());
    otherPartnerId = createPartner(otherTenant, otherTenantUser.id(), "Other customer " + suffix);
    TenantContext.clear();
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @ParameterizedTest
  @EnumSource(Mutation.class)
  void ownWriteDeniesAnotherCreatorWithoutChangingOrderOrAnyLine(Mutation mutation)
      throws Exception {
    grant(mutation, DataScope.GLOBAL, DataScope.OWN);
    SalesOrder order = createOrder(mutation, colleague);
    assertDeniedAndUnchanged(mutation, order);
  }

  @ParameterizedTest
  @EnumSource(Mutation.class)
  void ownWriteAllowsOwnOrderAndPersistsExpectedTransition(Mutation mutation) throws Exception {
    grant(mutation, DataScope.GLOBAL, DataScope.OWN);
    assertAllowed(mutation, createOrder(mutation, actor));
  }

  @ParameterizedTest
  @EnumSource(
      value = Mutation.class,
      names = {"CONFIRM", "SHIP", "CANCEL", "DELETE"})
  void globalWriteDoesNotReplaceMissingActionPair(Mutation mutation) throws Exception {
    grant(mutation, null, DataScope.GLOBAL);
    assertDeniedAndUnchanged(mutation, createOrder(mutation, colleague));
  }

  @ParameterizedTest
  @EnumSource(
      value = Mutation.class,
      names = {"CONFIRM", "SHIP", "CANCEL", "DELETE"})
  void actionPairDoesNotReplaceMissingWritePair(Mutation mutation) throws Exception {
    grant(mutation, DataScope.GLOBAL, null);
    assertDeniedAndUnchanged(mutation, createOrder(mutation, actor));
  }

  @ParameterizedTest
  @EnumSource(
      value = Mutation.class,
      names = {"CONFIRM", "SHIP", "CANCEL", "DELETE"})
  void ownActionWithGlobalWriteAllowsAnotherCreator(Mutation mutation) throws Exception {
    grant(mutation, DataScope.OWN, DataScope.GLOBAL);
    assertAllowed(mutation, createOrder(mutation, colleague));
  }

  @ParameterizedTest
  @EnumSource(
      value = Mutation.class,
      names = {"CONFIRM", "SHIP", "CANCEL", "DELETE"})
  void globalActionWithOwnWriteStillDeniesAnotherCreator(Mutation mutation) throws Exception {
    grant(mutation, DataScope.GLOBAL, DataScope.OWN);
    assertDeniedAndUnchanged(mutation, createOrder(mutation, colleague));
  }

  @ParameterizedTest
  @EnumSource(
      value = Mutation.class,
      names = {"PROCESS", "DELIVER", "HOLD", "RESUME", "REVISE"})
  void writeActionEndpointsDenyMissingWrite(Mutation mutation) throws Exception {
    grant(mutation, null, null);
    assertDeniedAndUnchanged(mutation, createOrder(mutation, actor));
  }

  @Test
  void departmentScopeIncludesSelfAndColleaguesButNotOtherDepartments() throws Exception {
    grant(Mutation.PROCESS, DataScope.GLOBAL, DataScope.DEPARTMENT);
    assertAllowed(Mutation.PROCESS, createOrder(Mutation.PROCESS, actor));
    assertAllowed(Mutation.PROCESS, createOrder(Mutation.PROCESS, colleague));
    assertDeniedAndUnchanged(Mutation.PROCESS, createOrder(Mutation.PROCESS, outsider));
  }

  @ParameterizedTest
  @EnumSource(
      value = DataScope.class,
      names = {"OWN", "DEPARTMENT"})
  void unknownCreatorIsDeniedWithoutCommercialOwnerFallback(DataScope scope) throws Exception {
    grant(Mutation.HOLD, DataScope.GLOBAL, scope);
    SalesOrder order = createOrder(Mutation.HOLD, actor);
    jdbc.update(
        "UPDATE sales_ord.sales_order SET created_by = NULL WHERE tenant_id = ? AND id = ?",
        order.getTenantId(),
        order.getId());
    assertDeniedAndUnchanged(Mutation.HOLD, order);
  }

  @ParameterizedTest
  @EnumSource(Mutation.class)
  void missingAndOtherTenantOrdersKeepExistingBadRequestContract(Mutation mutation)
      throws Exception {
    grant(mutation, DataScope.GLOBAL, DataScope.GLOBAL);
    SalesOrder otherOrder = createOrder(mutation, otherTenantUser);
    Snapshot before = snapshot(otherOrder);
    for (UUID id : List.of(UUID.randomUUID(), otherOrder.getId())) {
      perform(mutation, id)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("ORDER_RULE_VIOLATION"))
          .andExpect(jsonPath("$.detail").value("Sales order not found: " + id));
    }
    assertThat(snapshot(otherOrder)).isEqualTo(before);
  }

  @Test
  void deniedConfirmDoesNotRequestApprovalRunRulesOrPublishEvents() throws Exception {
    grant(Mutation.CONFIRM, DataScope.GLOBAL, DataScope.OWN);
    SalesOrder order = createOrder(Mutation.CONFIRM, colleague);
    clearInvocations(approvalPort, ruleEngine, eventPublisher);
    assertDeniedAndUnchanged(Mutation.CONFIRM, order);
    verify(approvalPort, never()).requiresApproval(any(), any(), any(), any(), any(), any());
    verify(ruleEngine, never()).processConfirmedOrder(any());
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  void deniedCancelDoesNotPublishCancellation() throws Exception {
    grant(Mutation.CANCEL, DataScope.GLOBAL, DataScope.OWN);
    SalesOrder order = createOrder(Mutation.CANCEL, colleague);
    clearInvocations(eventPublisher);
    assertDeniedAndUnchanged(Mutation.CANCEL, order);
    verify(eventPublisher, never()).publish(any(SalesOrderCancelledEvent.class));
  }

  @Test
  void approvalCallbacksPreserveAcceptRejectAndDraftRejection() {
    SalesOrder accepted = createOrder(Mutation.CONFIRM, actor);
    accepted.pendingApproval();
    salesOrderRepository.saveAndFlush(accepted);
    orderService.confirmOrderAsSystem(accepted.getId());
    assertThat(reload(accepted).getStatus()).isEqualTo(OrderStatus.CONFIRMED);

    SalesOrder rejected = createOrder(Mutation.CONFIRM, actor);
    rejected.pendingApproval();
    salesOrderRepository.saveAndFlush(rejected);
    orderService.rejectOrder(rejected.getId(), "Approval rejected");
    assertThat(reload(rejected).getStatus()).isEqualTo(OrderStatus.REJECTED);
    assertThat(reload(rejected).getRejectionReason()).isEqualTo("Approval rejected");

    SalesOrder draft = createOrder(Mutation.CONFIRM, actor);
    Snapshot before = snapshot(draft);
    assertThatThrownBy(() -> orderService.confirmOrderAsSystem(draft.getId()))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getHttpStatus()).isEqualTo(409));
    assertThat(snapshot(draft)).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void demoEntryPreservesBothApprovalBranchesWithoutUserPermissions(boolean approvalRequired) {
    SalesOrder order = createOrder(Mutation.CONFIRM, actor);
    when(approvalPort.requiresApproval(any(), any(), any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              assertThat((UUID) invocation.getArgument(1)).isEqualTo(actor.id());
              return approvalRequired;
            });
    orderService.confirmDemoSeedOrder(order.getId());
    assertThat(reload(order).getStatus())
        .isEqualTo(approvalRequired ? OrderStatus.PENDING_APPROVAL : OrderStatus.CONFIRMED);
  }

  private void grant(Mutation mutation, DataScope actionScope, DataScope writeScope) {
    actions.clear();
    if (!mutation.action.equals("write") && actionScope != null) {
      actions.put(mutation.action, actionScope);
    }
    if (writeScope != null) {
      actions.put("write", writeScope);
    }
    Cache cache = cacheManager.getCache("permissions");
    if (cache != null) {
      cache.clear();
    }
  }

  private ResultActions perform(Mutation mutation, UUID orderId) throws Exception {
    TenantContext.setCurrentTenantId(actor.tenantId());
    TenantContext.setCurrentUserId(actor.id());
    AuthenticatedUserContext context =
        new AuthenticatedUserContext(
            actor.id(), "WORKER", actor.departmentCodes(), null, actor.tenantId());
    var token = new UsernamePasswordAuthenticationToken(context, "n/a", List.of());
    token.setDetails(context);
    MockHttpServletRequestBuilder request =
        mutation == Mutation.DELETE
            ? delete("/api/v1/sales/orders/{id}", orderId)
            : post("/api/v1/sales/orders/{id}/" + mutation.path, orderId);
    return mockMvc.perform(request.with(authentication(token)).with(csrf()));
  }

  private void assertDeniedAndUnchanged(Mutation mutation, SalesOrder order) throws Exception {
    Snapshot before = snapshot(order);
    assertThat(before.lines()).hasSize(2);
    assertThat(before.order().get("is_active")).isEqualTo(true);
    assertThat(before.lines())
        .allSatisfy(line -> assertThat(line.get("is_active")).isEqualTo(true));
    perform(mutation, order.getId()).andExpect(status().isForbidden());
    assertThat(snapshot(order)).isEqualTo(before);
  }

  private void assertAllowed(Mutation mutation, SalesOrder order) throws Exception {
    var response = perform(mutation, order.getId()).andExpect(status().isOk());
    SalesOrder saved = reload(order);
    assertThat(saved.getStatus()).isEqualTo(mutation.result);
    assertThat(saved.getVersion()).isGreaterThan(order.getVersion());
    assertThat(saved.getIsActive()).isEqualTo(mutation != Mutation.DELETE);
    if (mutation == Mutation.DELETE) {
      assertThat(snapshot(order).lines())
          .hasSize(2)
          .allSatisfy(line -> assertThat(line.get("is_active")).isEqualTo(false));
    } else {
      response.andExpect(jsonPath("$.data.status").value(mutation.result.name()));
    }
    if (mutation == Mutation.DELIVER) {
      assertThat(saved.getActualDeliveryDate()).isEqualTo(LocalDate.now());
    } else if (mutation == Mutation.HOLD) {
      assertThat(saved.getStatusBeforeHold()).isEqualTo(mutation.initial);
    } else if (mutation == Mutation.RESUME) {
      assertThat(saved.getStatusBeforeHold()).isNull();
    } else if (mutation == Mutation.REVISE) {
      assertThat(saved.getRejectionReason()).isNull();
    }
  }

  private SalesOrder reload(SalesOrder order) {
    TenantContext.setCurrentTenantId(order.getTenantId());
    return salesOrderRepository
        .findByTenantIdAndId(order.getTenantId(), order.getId())
        .orElseThrow();
  }

  private Snapshot snapshot(SalesOrder order) {
    TenantContext.setCurrentTenantId(order.getTenantId());
    return new Snapshot(
        jdbc.queryForMap(
            "SELECT * FROM sales_ord.sales_order WHERE tenant_id = ? AND id = ?",
            order.getTenantId(),
            order.getId()),
        jdbc.queryForList(
            "SELECT * FROM sales_ord.sales_order_line WHERE tenant_id = ? AND sales_order_id = ? ORDER BY id",
            order.getTenantId(),
            order.getId()));
  }

  private SalesOrder createOrder(Mutation mutation, TestUser creator) {
    TenantContext.setCurrentTenantId(creator.tenantId());
    TenantContext.setCurrentUserId(creator.id());
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(
                creator.tenantId().equals(actor.tenantId()) ? partnerId : otherPartnerId)
            .orderNumber("SO-" + UUID.randomUUID())
            .status(mutation.initial)
            .statusBeforeHold(mutation == Mutation.RESUME ? OrderStatus.CONFIRMED : null)
            .rejectionReason(mutation == Mutation.REVISE ? "Rejected fixture" : null)
            .orderDate(LocalDate.now())
            .totals(OrderTotals.zero("GBP"))
            .build();
    order = salesOrderRepository.saveAndFlush(order);
    for (int index = 0; index < 2; index++) {
      lineRepository.saveAndFlush(
          SalesOrderLine.builder()
              .salesOrderId(order.getId())
              .productDesc("Scope fixture " + index)
              .requestedQty(BigDecimal.TEN)
              .unit("KG")
              .unitPrice(Money.of(BigDecimal.ONE, "GBP"))
              .build());
    }
    return order;
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

  private record Snapshot(Map<String, Object> order, List<Map<String, Object>> lines) {}

  private record TenantFixture(
      UUID tenantId,
      Organization organization,
      Role role,
      Department salesDepartment,
      Department financeDepartment) {}

  private record TestUser(UUID tenantId, UUID id, List<String> departmentCodes) {}

  private enum Mutation {
    CONFIRM("confirm", "confirm", OrderStatus.DRAFT, OrderStatus.CONFIRMED),
    PROCESS("process", "write", OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS),
    SHIP("ship", "ship", OrderStatus.IN_PROGRESS, OrderStatus.SHIPPED),
    DELIVER("deliver", "write", OrderStatus.SHIPPED, OrderStatus.DELIVERED),
    CANCEL("cancel", "cancel", OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
    HOLD("hold", "write", OrderStatus.CONFIRMED, OrderStatus.ON_HOLD),
    RESUME("resume", "write", OrderStatus.ON_HOLD, OrderStatus.CONFIRMED),
    REVISE("revise", "write", OrderStatus.REJECTED, OrderStatus.DRAFT),
    DELETE("", "delete", OrderStatus.DRAFT, OrderStatus.DRAFT);

    final String path;
    final String action;
    final OrderStatus initial;
    final OrderStatus result;

    Mutation(String path, String action, OrderStatus initial, OrderStatus result) {
      this.path = path;
      this.action = action;
      this.initial = initial;
      this.result = result;
    }
  }
}
