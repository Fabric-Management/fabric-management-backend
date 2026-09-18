package com.fabricmanagement.flowboard.routing.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.flowboard.routing.app.listener.RoutingEventListener;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.event.*;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.app.*;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.platform.organization.domain.*;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.tradingpartner.domain.*;
import com.fabricmanagement.platform.tradingpartner.infra.repository.*;
import com.fabricmanagement.platform.user.domain.*;
import com.fabricmanagement.platform.user.infra.repository.*;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real permissions, sales scope, PostgreSQL locks and transaction boundaries. */
abstract class RoutingIntegrationSupport extends AbstractIntegrationTest {
  protected JdbcTemplate jdbc;
  @Autowired protected PlatformTransactionManager manager;
  @Autowired protected RoutingRepository repository;
  @Autowired protected RoutingEvaluationService routing;
  @Autowired protected RoutingPoolConfigurationService configuration;
  @Autowired protected RoutingRepairService repair;
  @Autowired protected RoutingFailureAlertService alerts;
  @Autowired protected RoutingQueryService queries;
  @Autowired protected PermissionEvaluator evaluator;
  @Autowired protected TaskProvisioningService provisioning;
  @Autowired protected TaskRepository tasks;
  @Autowired protected TenantRepository tenants;
  @Autowired protected OrganizationRepository organizations;
  @Autowired protected RoleRepository roles;
  @Autowired protected UserRepository users;
  @Autowired protected PermissionTemplateRepository templates;
  @Autowired protected TradingPartnerRegistryRepository registries;
  @Autowired protected TradingPartnerRepository partners;
  @Autowired protected SalesOrderRepository orders;
  @MockitoSpyBean protected RoutingEventListener listener;
  protected UUID tenant;
  protected UUID board;
  protected Organization organization;
  protected String suffix;

  @Autowired
  void routingJdbc(@Qualifier("dataSource") DataSource dataSource) {
    // Lock probes and transactional fixture writes must use the same pool as production routing.
    jdbc = new JdbcTemplate(dataSource);
  }

  @BeforeEach
  void routingFixture() {
    // Simulate the process stopping before asynchronous delivery. Publications remain owed.
    RoutingEventListener listenerTarget = listenerTarget();
    doThrow(new IllegalStateException("simulated listener unavailable"))
        .when(listenerTarget)
        .onRoutingPoolChanged(any(RoutingPoolChangedEvent.class));
    doThrow(new IllegalStateException("simulated listener unavailable"))
        .when(listenerTarget)
        .onRoutingFailureOpened(any(RoutingFailureOpenedEvent.class));
    suffix = UUID.randomUUID().toString().substring(0, 8);
    tenant = tenants.saveAndFlush(Tenant.create("Routing " + suffix, "RT-" + suffix)).getId();
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentTenantUid("RT-" + suffix);
    board = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO flowboard.board (id, tenant_id, uid, name, board_type, wip_limit_default, default_view_type,
          is_active, created_at, updated_at, version)
        VALUES (?, ?, ?, 'Routing test', 'GLOBAL', 5, 'KANBAN', true, now(), now(), 0)
        """,
        board,
        tenant,
        board.toString());
    organization =
        organizations.saveAndFlush(
            Organization.create("Routing org " + suffix, "RT-" + suffix, OrganizationType.SPINNER));
  }

  protected RoutingEventListener listenerTarget() {
    return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(listener);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  protected User user(String name, String... permissions) {
    Role role =
        roles.saveAndFlush(
            Role.create(
                name + suffix,
                "RT-" + UUID.randomUUID().toString().substring(0, 8),
                "Routing test"));
    User user = User.create(name, suffix, organization.getId());
    user.setRole(role);
    user = users.saveAndFlush(user);
    for (String permission : permissions) {
      String[] pair = permission.split(":");
      templates.saveAndFlush(
          PermissionTemplate.builder()
              .roleCode(role.getRoleCode())
              .resource(pair[0])
              .action(pair[1])
              .dataScope(DataScope.GLOBAL)
              .build());
    }
    return user;
  }

  protected Task task(UUID creator) {
    TenantContext.setCurrentUserId(creator);
    var registry = TradingPartnerRegistry.create(null, "Routing customer", "GBR");
    registry.setUid("RTR-" + UUID.randomUUID());
    registry = registries.saveAndFlush(registry);
    var partner =
        partners.saveAndFlush(
            TradingPartner.create(registry, PartnerType.CUSTOMER, "Routing customer"));
    var order =
        orders.saveAndFlush(
            SalesOrder.builder()
                .tradingPartnerId(partner.getId())
                .orderNumber("RT-" + UUID.randomUUID())
                .status(OrderStatus.DRAFT)
                .orderDate(LocalDate.now())
                .totals(OrderTotals.zero("GBP"))
                .build());
    return provisioning.createOrSynchronizeActive(
        new TaskCreation(
            board,
            "Routing test",
            null,
            TaskType.ORDER_COVER,
            com.fabricmanagement.flowboard.task.domain.ModuleType.GENERAL,
            Priority.HIGH,
            null,
            null,
            "SALES_ORDER",
            order.getId(),
            "TEMPLATE",
            UUID.randomUUID(),
            TaskGenerationKey.subject("SALES_ORDER", order.getId(), TaskType.ORDER_COVER, null),
            Set.of()));
  }

  protected void configure(UUID... members) {
    Long revision =
        repository.pool(tenant, RoutingPoolKey.ORDER_COVER).map(p -> p.revision()).orElse(null);
    configuration.configure(tenant, RoutingPoolKey.ORDER_COVER, revision, Set.of(members));
  }

  protected RoutingRecords.Evaluation evaluate(Task task) {
    return routing.evaluate(tenant, RoutingPoolKey.ORDER_COVER, task.getId());
  }

  protected Set<UUID> assignees(Task task) {
    return new HashSet<>(
        jdbc.queryForList(
            "SELECT user_id FROM flowboard.task_assignee WHERE tenant_id = ? AND task_id = ? AND is_active",
            UUID.class,
            tenant,
            task.getId()));
  }

  protected int count(String table) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, tenant);
  }

  protected UUID failure(Task task) {
    return repository.openFailures(tenant, task.getId()).getFirst().id();
  }

  protected <T> T tx(Supplier<T> work) {
    return new TransactionTemplate(manager).execute(status -> work.get());
  }

  protected <T> T inTenant(Supplier<T> work) {
    return TenantContext.executeInTenantContext(tenant, work);
  }

  protected void permission(User user, String key, boolean active) {
    String[] pair = key.split(":");
    jdbc.update(
        "UPDATE common_user.permission_template SET is_active = ? WHERE tenant_id = ? AND role_code = ? AND resource = ? AND action = ?",
        active,
        tenant,
        user.getRole().getRoleCode(),
        pair[0],
        pair[1]);
  }

  protected void seedNotificationTemplate() {
    jdbc.update(
        """
        INSERT INTO i18n.translation_key (id, tenant_id, uid, key_code, module, default_value)
        VALUES (gen_random_uuid(), ?, gen_random_uuid()::text, 'routing.test.title', 'NOTIFICATION', 'Routing failure'),
               (gen_random_uuid(), ?, gen_random_uuid()::text, 'routing.test.body', 'NOTIFICATION', 'Task {taskId}: {failureId}')
        ON CONFLICT (tenant_id, key_code) DO NOTHING
        """,
        tenant,
        tenant);
    jdbc.update(
        """
        INSERT INTO i18n.translation_value (id, tenant_id, uid, translation_key_id, locale, value, is_override)
        SELECT gen_random_uuid(), tenant_id, gen_random_uuid()::text, id, 'EN', default_value, false
        FROM i18n.translation_key WHERE tenant_id = ? AND key_code IN ('routing.test.title','routing.test.body')
        ON CONFLICT (translation_key_id, locale, tenant_id) DO NOTHING
        """,
        tenant);
    jdbc.update(
        """
        INSERT INTO notification.notification_template
          (id, tenant_id, uid, event_type, channel, title_key, body_key, importance, delivery_type)
        VALUES (gen_random_uuid(), ?, gen_random_uuid()::text, 'ROUTING_FAILURE', 'IN_APP',
          'routing.test.title', 'routing.test.body', 'CRITICAL', 'INSTANT')
        ON CONFLICT (tenant_id, event_type, channel) DO UPDATE SET is_active = true
        """,
        tenant);
  }

  protected static void await(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS))
        throw new AssertionError("Timed out awaiting race boundary");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new AssertionError(error);
    }
  }

  protected static void await(CountDownLatch latch, Future<?> worker) throws Exception {
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(20))
        .until(() -> latch.getCount() == 0 || worker.isDone());
    if (worker.isDone()) worker.get();
    if (latch.getCount() != 0) {
      throw new AssertionError("Race worker completed without reaching the expected boundary");
    }
  }

  protected void awaitBlocked(java.util.concurrent.atomic.AtomicInteger pid, Future<?> worker)
      throws Exception {
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(15))
        .until(
            () ->
                worker.isDone()
                    || (pid.get() > 0
                        && Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                "SELECT cardinality(pg_blocking_pids(?)) > 0",
                                Boolean.class,
                                pid.get()))));
    if (worker.isDone()) {
      worker.get(); // Surface the actual SQL/transaction error instead of a misleading timeout.
      throw new AssertionError(
          "Race worker completed without waiting on the expected database lock");
    }
  }

  protected <T> List<T> race(Supplier<T> first, Supplier<T> second) throws Exception {
    CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a =
          executor.submit(
              () ->
                  inTenant(
                      () -> {
                        ready.countDown();
                        await(start);
                        return first.get();
                      }));
      var b =
          executor.submit(
              () ->
                  inTenant(
                      () -> {
                        ready.countDown();
                        await(start);
                        return second.get();
                      }));
      await(ready);
      start.countDown();
      return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
    }
  }
}
