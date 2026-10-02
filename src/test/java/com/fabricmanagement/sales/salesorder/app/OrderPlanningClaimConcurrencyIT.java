package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
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
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fabricmanagement.testsupport.PostgresImage;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Two planners taking the same order in real, overlapping transactions: exactly one becomes
 * responsible, the other is told someone else took it, and the order moves into planning once.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
class OrderPlanningClaimConcurrencyIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("planning_claim_test")
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

  @Autowired private TenantRepository tenants;
  @Autowired private OrganizationRepository organizations;
  @Autowired private RoleRepository roles;
  @Autowired private DepartmentRepository departments;
  @Autowired private UserRepository users;
  @Autowired private UserDepartmentRepository userDepartments;
  @Autowired private TradingPartnerRegistryRepository registries;
  @Autowired private TradingPartnerRepository partners;
  @Autowired private SalesOrderRepository orders;
  @Autowired private OrderFlowService flow;
  @Autowired private OrderWorkService work;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private PermissionEvaluator permissionEvaluator;

  private UUID tenantId;
  private UUID first;
  private UUID second;
  private UUID orderId;

  @BeforeEach
  void setUp() {
    PermissionResult planner =
        new PermissionResult(
            Map.of(
                "production",
                Map.of(
                    "read", DataScope.OWN,
                    "write", DataScope.OWN,
                    "claim", DataScope.DEPARTMENT)),
            false);
    when(permissionEvaluator.evaluate(any(), any(), any(), any())).thenReturn(planner);
    when(permissionEvaluator.evaluateFresh(any(), any(), any(), any())).thenReturn(planner);

    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tenantId = tenants.save(Tenant.create("Claim " + suffix, "PC-" + suffix)).getId();
    TenantContext.setCurrentTenantId(tenantId);
    Organization organization =
        organizations.save(
            Organization.create("Claim Org " + suffix, "TAX-" + suffix, OrganizationType.WEAVER));
    Role role = roles.save(Role.create("Planner " + suffix, "WORKER", "Claim test"));
    Department planning =
        departments.save(
            Department.create(organization.getId(), "Planning", "PLANNING", "Planning"));
    first = planner(organization, role, planning, "First");
    second = planner(organization, role, planning, "Second");

    TenantContext.setCurrentUserId(first);
    TradingPartnerRegistry registry =
        TradingPartnerRegistry.create(null, "Claim customer " + suffix, "GBR");
    registry.setUid("REG-" + UUID.randomUUID());
    UUID partnerId =
        partners
            .saveAndFlush(
                TradingPartner.create(
                    registries.save(registry), PartnerType.CUSTOMER, "Claim customer"))
            .getId();
    orderId =
        orders
            .saveAndFlush(
                SalesOrder.builder()
                    .tradingPartnerId(partnerId)
                    .orderNumber("SO-" + suffix)
                    .status(OrderStatus.DRAFT)
                    .orderDate(LocalDate.now())
                    .build())
            .getId();
    jdbc.update(
        "UPDATE sales_ord.sales_order SET flow_stage = 'AWAITING_PLANNING', planning_round = 1"
            + " WHERE tenant_id = ? AND id = ?",
        tenantId,
        orderId);
    transactions.executeWithoutResult(status -> work.route(orderId, OrderWorkKind.PLANNING, null));
    TenantContext.clear();
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void ofTwoOverlappingClaimsExactlyOneSucceeds() throws Exception {
    CountDownLatch firstHoldsTheLock = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // The first claim keeps its transaction open after taking the order...
      CompletableFuture<Object> firstClaim =
          CompletableFuture.supplyAsync(
              () ->
                  inTenant(
                      first,
                      () ->
                          transactions.execute(
                              status -> {
                                Object result = flow.claim(orderId, first);
                                firstHoldsTheLock.countDown();
                                sleep(700);
                                return result;
                              })),
              pool);
      // ...while the second one tries the same order and has to wait for it.
      assertThat(firstHoldsTheLock.await(10, TimeUnit.SECONDS)).isTrue();
      long started = System.nanoTime();
      CompletableFuture<Object> secondClaim =
          CompletableFuture.supplyAsync(
              () -> inTenant(second, () -> flow.claim(orderId, second)), pool);

      Object firstResult = firstClaim.get(20, TimeUnit.SECONDS);
      Object secondResult = secondClaim.get(20, TimeUnit.SECONDS);
      long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

      assertThat(firstResult).isNotInstanceOf(Throwable.class);
      assertThat(secondResult)
          .isInstanceOf(OrderDomainException.class)
          .extracting(error -> ((OrderDomainException) error).getErrorCode())
          .isEqualTo("WORK_ALREADY_TAKEN");
      assertThat(waitedMillis).as("the second claim waited for the first").isGreaterThan(300);
    } finally {
      pool.shutdownNow();
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT assignee_id FROM sales_ord.order_work_assignment"
                    + " WHERE tenant_id = ? AND sales_order_id = ? AND work_kind = 'PLANNING'",
                UUID.class,
                tenantId,
                orderId))
        .isEqualTo(first);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.order_work_assignment_event"
                    + " WHERE tenant_id = ? AND sales_order_id = ? AND event_type = 'CLAIMED'",
                Integer.class,
                tenantId,
                orderId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.order_flow_event"
                    + " WHERE tenant_id = ? AND sales_order_id = ? AND to_stage = 'IN_PLANNING'",
                Integer.class,
                tenantId,
                orderId))
        .isEqualTo(1);
  }

  @Test
  void simultaneousClaimsLeaveOneResponsiblePlanner() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<Object> a =
          CompletableFuture.supplyAsync(
              () -> inTenant(first, () -> awaitThen(start, () -> flow.claim(orderId, first))),
              pool);
      CompletableFuture<Object> b =
          CompletableFuture.supplyAsync(
              () -> inTenant(second, () -> awaitThen(start, () -> flow.claim(orderId, second))),
              pool);
      start.countDown();
      var results = java.util.List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));

      assertThat(results).filteredOn(result -> !(result instanceof Throwable)).hasSize(1);
      assertThat(results)
          .filteredOn(OrderDomainException.class::isInstance)
          .singleElement()
          .extracting(error -> ((OrderDomainException) error).getErrorCode())
          .isEqualTo("WORK_ALREADY_TAKEN");
    } finally {
      pool.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.order_work_assignment_event"
                    + " WHERE tenant_id = ? AND sales_order_id = ? AND event_type = 'CLAIMED'",
                Integer.class,
                tenantId,
                orderId))
        .isEqualTo(1);
  }

  /** Runs in the tenant as the user; a failure is returned rather than thrown. */
  private Object inTenant(UUID user, java.util.function.Supplier<Object> step) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(user);
    try {
      return step.get();
    } catch (RuntimeException failure) {
      return failure;
    } finally {
      TenantContext.clear();
    }
  }

  private static Object awaitThen(CountDownLatch start, java.util.function.Supplier<Object> step) {
    try {
      start.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
    return step.get();
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private UUID planner(Organization organization, Role role, Department planning, String name) {
    User user = User.create(name, "Planner", organization.getId());
    user.setRole(role);
    user = users.save(user);
    userDepartments.save(UserDepartment.create(user, planning, true, user.getId()));
    return user.getId();
  }
}
