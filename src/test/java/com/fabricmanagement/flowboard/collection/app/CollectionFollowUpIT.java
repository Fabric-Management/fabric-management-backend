package com.fabricmanagement.flowboard.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.*;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.flowboard.board.domain.*;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
import com.fabricmanagement.flowboard.collection.domain.*;
import com.fabricmanagement.flowboard.collection.dto.CollectionFollowUpDtos.*;
import com.fabricmanagement.flowboard.collection.infra.repository.*;
import com.fabricmanagement.flowboard.decision.domain.DecisionQueueBucket;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionQueueRepository;
import com.fabricmanagement.flowboard.routing.app.*;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.port.out.RoutingUserQueryPort.UserPermissions;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.*;
import com.fabricmanagement.platform.organization.domain.*;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.user.domain.*;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.sales.salesorder.domain.port.SalesOrderReadScopePort.OrderReadScope;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL exercises the actual work/contact/receipt transaction and the shared queue SQL. */
@Import(CollectionFollowUpIT.FixedTime.class)
class CollectionFollowUpIT extends AbstractIntegrationTest {
  @TestConfiguration
  static class FixedTime {
    @Bean
    @Primary
    Clock followUpClock() {
      return Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired CollectionFollowUpService service;
  @Autowired CollectionFollowUpRepository cases;
  @Autowired CollectionContactRepository contacts;
  @Autowired TaskAssigneeRepository assignees;
  @Autowired TaskRepository tasks;
  @Autowired BoardRepository boards;
  @Autowired OrganizationRepository organisations;
  @Autowired UserRepository users;
  @Autowired com.fabricmanagement.platform.user.infra.repository.RoleRepository roles;
  @Autowired DecisionQueueRepository queue;
  @Autowired PlatformTransactionManager manager;
  @Autowired SystemTransactionExecutor system;
  @MockitoBean CollectionFollowUpPort source;
  @MockitoBean RoutingEvaluationService routing;
  @MockitoBean RoutingEligibilityService eligibility;
  @MockitoBean RoutingRepository pools;
  @MockitoBean DomainEventPublisher events;
  @MockitoBean CollectionFollowUpJob job;
  @MockitoSpyBean CollectionFollowUpCommandRepository receipts;
  JdbcTemplate jdbc;
  UUID tenant, otherTenant, actor, obligation, caseId;
  final AtomicReference<BigDecimal> outstanding = new AtomicReference<>();

  @Autowired
  void dataSource(@Qualifier("dataSource") DataSource ds) {
    jdbc = new JdbcTemplate(ds);
  }

  @BeforeEach
  void seed() {
    tenant = UUID.randomUUID();
    otherTenant = UUID.randomUUID();
    obligation = UUID.randomUUID();
    outstanding.set(new BigDecimal("4284.00"));
    system.executeInTransaction(
        sql -> {
          for (UUID id : List.of(tenant, otherTenant))
            sql.update(
                "INSERT INTO common_tenant.common_tenant(id,uid,slug,name,status)"
                    + " VALUES(?,?,?,'Northbridge Cloth','ACTIVE')",
                id,
                id.toString(),
                id.toString());
          return null;
        });
    actor =
        in(
            tenant,
            () -> {
              var org =
                  organisations.saveAndFlush(
                      Organization.create(
                          "Northbridge Cloth", "NB-" + tenant, OrganizationType.SPINNER));
              var draft = User.create("Harriet", "Ward", org.getId());
              draft.setRole(
                  roles.saveAndFlush(
                      com.fabricmanagement.platform.user.domain.Role.create(
                          "Collection colleagues",
                          "S5-" + tenant.toString().substring(0, 8),
                          "Collection follow-up test")));
              var user = users.saveAndFlush(draft);
              boards.saveAndFlush(
                  Board.create("Collections", BoardType.GLOBAL, 5, ViewType.KANBAN, null));
              return user.getId();
            });
    doAnswer(
            a -> {
              jdbc.queryForObject(
                  "select 1 from pg_advisory_xact_lock(hashtextextended(cast(? as text),41004))",
                  Integer.class,
                  TenantContext.requireTenantId());
              return null;
            })
        .when(source)
        .lock();
    when(source.current(obligation)).thenAnswer(a -> facts());
    when(source.mayRead(eq(actor), eq(obligation)))
        .thenAnswer(a -> tenant.equals(TenantContext.requireTenantId()));
    when(source.dayOf(any()))
        .thenAnswer(
            call ->
                ((Instant) call.getArgument(0)).atZone(ZoneId.of("Europe/London")).toLocalDate());
    when(source.today()).thenReturn(LocalDate.of(2026, 10, 22));
    when(eligibility.user(tenant, actor))
        .thenReturn(
            new UserPermissions(
                tenant,
                actor,
                "Harriet Ward",
                true,
                new PermissionResult(
                    Map.of(
                        "flowboard",
                        Map.of("read", DataScope.OWN, "write", DataScope.OWN),
                        "finance",
                        Map.of("read", DataScope.OWN)),
                    false)));
    when(eligibility.eligibility(
            eq(tenant), eq(obligation), any(), eq(true), eq(RoutingPoolKey.COLLECTION_FOLLOW_UP)))
        .thenReturn(List.of());
    when(pools.isActiveMember(tenant, RoutingPoolKey.COLLECTION_FOLLOW_UP, actor)).thenReturn(true);
    in(
        tenant,
        () -> {
          service.synchronize(obligation);
          var c =
              cases
                  .findFirstByTenantIdAndObligationIdOrderByEpisodeDesc(tenant, obligation)
                  .orElseThrow();
          caseId = c.getId();
          assignees.saveAndFlush(
              TaskAssignee.assignToUser(c.getTaskId(), actor, AssignedBy.SYSTEM));
          service.synchronize(obligation);
          return null;
        });
  }

  CollectionFollowUpPort.Facts facts() {
    return new CollectionFollowUpPort.Facts(
        obligation,
        7,
        UUID.fromString("39268e29-a908-492d-8ea2-7a3c1a7b6271"),
        "GBP",
        new BigDecimal("4284.00"),
        outstanding.get(),
        BigDecimal.ZERO,
        LocalDate.of(2026, 10, 20),
        null,
        false,
        false,
        false,
        false,
        actor);
  }

  <T> T in(UUID tenant, Supplier<T> work) {
    return TenantContext.executeInTenantContext(
        tenant,
        () -> {
          TenantContext.setCurrentTenantUid("NB-COLLECTIONS");
          TenantContext.setCurrentUserId(actor);
          return new TransactionTemplate(manager).execute(status -> work.get());
        });
  }

  ContactRequest request(Detail d, UUID operation) {
    return new ContactRequest(
        operation,
        d.version(),
        d.obligationVersion(),
        d.taskVersion(),
        CollectionContact.Outcome.PROMISE_TO_PAY,
        Instant.parse("2026-10-22T11:30:00Z"),
        LocalDate.of(2026, 11, 2),
        new BigDecimal("1000.00"),
        LocalDate.of(2026, 11, 2),
        "Customer promised a bank transfer");
  }

  @AfterEach
  void clean() {
    TenantContext.clear();
    system.executeInTransaction(
        sql -> {
          for (UUID id : List.of(tenant, otherTenant)) {
            sql.queryForObject(
                "SELECT set_config('app.collection_purge_tenant',?,true)",
                String.class,
                id.toString());
            for (String table :
                List.of(
                    "collection_follow_up_command",
                    "collection_contact",
                    "collection_follow_up_scan",
                    "collection_follow_up",
                    "task_affected_subject",
                    "task_assignee",
                    "task"))
              sql.update("DELETE FROM flowboard." + table + " WHERE tenant_id=?", id);
          }
          return null;
        });
  }

  @Test
  void promiseSurvivesReopenAndDoesNotPostMoneyOrMoveTheAgreedDate() {
    var d = in(tenant, () -> service.detail(caseId, actor));
    var request = request(d, UUID.randomUUID());
    in(tenant, () -> service.recordContact(caseId, actor, request));
    var reopened = in(tenant, () -> service.detail(caseId, actor));
    assertThat(reopened.agreedDate()).isEqualTo(LocalDate.of(2026, 10, 20));
    assertThat(reopened.promisedOn()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(reopened.recordedOutstanding()).isEqualTo("4284.00");
    assertThat(
            in(
                tenant,
                () ->
                    jdbc.queryForObject(
                        "SELECT count(*) FROM finance.finance_payment WHERE tenant_id=?",
                        Integer.class,
                        tenant)))
        .isZero();
    assertThat(in(tenant, () -> service.contacts(caseId, actor, 0, 20).getContent())).hasSize(1);
  }

  @Test
  void concurrentIdenticalCommandHasOneContactAndOneDurableReceipt() throws Exception {
    var request = request(in(tenant, () -> service.detail(caseId, actor)), UUID.randomUUID());
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                start.await();
                return in(tenant, () -> service.recordContact(caseId, actor, request));
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                return in(tenant, () -> service.recordContact(caseId, actor, request));
              });
      start.countDown();
      var a = first.get(15, TimeUnit.SECONDS);
      var b = second.get(15, TimeUnit.SECONDS);
      assertThat(a.contactId()).isEqualTo(b.contactId());
      assertThat(List.of(a.replayed(), b.replayed())).containsExactlyInAnyOrder(false, true);
    }
    assertThat(in(tenant, () -> service.contacts(caseId, actor, 0, 20).getTotalElements()))
        .isEqualTo(1);
  }

  @Test
  void failedReceiptPersistenceRollsBackContactAndFollowUpDate() {
    var d = in(tenant, () -> service.detail(caseId, actor));
    doThrow(new IllegalStateException("Receipt store interrupted"))
        .when(receipts)
        .saveAndFlush(any());
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () -> service.recordContact(caseId, actor, request(d, UUID.randomUUID()))))
        .hasMessageContaining("interrupted");
    assertThat(in(tenant, () -> service.contacts(caseId, actor, 0, 20).getContent())).isEmpty();
    assertThat(in(tenant, () -> service.detail(caseId, actor).nextFollowUpOn())).isNull();
  }

  @Test
  void aReversalCreatesAnotherEpisodeAndKeepsTheOldEvidence() {
    var old = in(tenant, () -> service.detail(caseId, actor));
    in(tenant, () -> service.recordContact(caseId, actor, request(old, UUID.randomUUID())));
    outstanding.set(BigDecimal.ZERO);
    in(
        tenant,
        () -> {
          service.synchronize(obligation);
          return null;
        });
    assertThat(in(tenant, () -> service.detail(caseId, actor).closedAt())).isNotNull();
    outstanding.set(new BigDecimal("4284.00"));
    in(
        tenant,
        () -> {
          service.synchronize(obligation);
          return null;
        });
    var latest =
        in(
            tenant,
            () ->
                cases
                    .findFirstByTenantIdAndObligationIdOrderByEpisodeDesc(tenant, obligation)
                    .orElseThrow());
    assertThat(latest.getEpisode()).isEqualTo(2);
    assertThat(latest.getId()).isNotEqualTo(caseId);
    assertThat(in(tenant, () -> service.contacts(caseId, actor, 0, 20).getContent())).hasSize(1);
  }

  @Test
  void financeOnlyQueueUsesFinanceScopeBeforePaginationAndDateFilters() {
    var page =
        in(
            tenant,
            () ->
                queue.page(
                    tenant,
                    actor,
                    Set.of(),
                    OrderReadScope.none(),
                    DecisionQueueBucket.MINE,
                    0,
                    20,
                    OrderReadScope.principals(Set.of(actor))));
    assertThat(page.total()).isEqualTo(1);
    assertThat(page.rows().getFirst().kind()).isEqualTo("COLLECTION_FOLLOW_UP");
    in(
        tenant,
        () ->
            service.recordContact(
                caseId, actor, request(service.detail(caseId, actor), UUID.randomUUID())));
    assertThat(
            in(
                tenant,
                () ->
                    queue.count(
                        tenant,
                        actor,
                        Set.of(),
                        OrderReadScope.none(),
                        DecisionQueueBucket.MINE,
                        OrderReadScope.principals(Set.of(actor)))))
        .isZero();
    assertThat(outstanding.get()).isEqualByComparingTo("4284");
  }

  @Test
  void crossTenantReadsAndMutableHistoryAreRejected() {
    assertThatThrownBy(() -> in(otherTenant, () -> service.detail(caseId, actor)))
        .hasMessageContaining("not found");
    var result =
        in(
            tenant,
            () ->
                service.recordContact(
                    caseId, actor, request(service.detail(caseId, actor), UUID.randomUUID())));
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () -> {
                      jdbc.update(
                          "UPDATE flowboard.collection_contact SET note='rewritten' WHERE id=?",
                          result.contactId());
                      return null;
                    }))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void rlsAppliesWithoutARepositoryTenantPredicate() {
    system.executeInTransaction(
        sql -> {
          sql.execute(
              "DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE"
                  + " rolname='collection_follow_up_probe') THEN CREATE ROLE"
                  + " collection_follow_up_probe NOLOGIN NOBYPASSRLS; END IF; END $$");
          sql.execute("GRANT USAGE ON SCHEMA flowboard TO collection_follow_up_probe");
          sql.execute(
              "GRANT SELECT ON flowboard.collection_follow_up TO collection_follow_up_probe");
          return null;
        });
    assertThat(
            in(
                otherTenant,
                () -> {
                  jdbc.execute("SET LOCAL ROLE collection_follow_up_probe");
                  jdbc.execute("SET LOCAL row_security=on");
                  return jdbc.queryForObject(
                      "SELECT count(*) FROM flowboard.collection_follow_up", Integer.class);
                }))
        .isZero();
    assertThat(
            in(
                tenant,
                () -> {
                  jdbc.execute("SET LOCAL ROLE collection_follow_up_probe");
                  return jdbc.queryForObject(
                      "SELECT count(*) FROM flowboard.collection_follow_up", Integer.class);
                }))
        .isEqualTo(1);
  }
}
