package com.fabricmanagement.flowboard.decision.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.flowboard.decision.domain.DecisionQueueBucket;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionQueueRepository;
import com.fabricmanagement.flowboard.routing.app.RoutingEligibilityService;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.routing.domain.port.out.RoutingUserQueryPort.UserPermissions;
import com.fabricmanagement.flowboard.routing.domain.port.out.SalesOrderWriteScopePort;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.app.DecisionBlockedException;
import com.fabricmanagement.flowboard.task.app.OrderCoverCaseOpenedListener;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.salesorder.domain.port.SalesOrderReadScopePort;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.access.AccessDeniedException;

class DecisionQueueServiceTest {
  private final UUID tenant = UUID.randomUUID();
  private final UUID caller = UUID.randomUUID();
  private final DecisionQueueRepository repository = mock(DecisionQueueRepository.class);
  private final SalesOrderReadScopePort reads = mock(SalesOrderReadScopePort.class);
  private final SalesOrderWriteScopePort writes = mock(SalesOrderWriteScopePort.class);
  private final UserFacade users = mock(UserFacade.class);
  private final RoutingEligibilityService eligibility = mock(RoutingEligibilityService.class);
  private final RoutingRepository routing = mock(RoutingRepository.class);
  private final DecisionProjectionRebuildState rebuild = new DecisionProjectionRebuildState();
  private final Clock clock = Clock.fixed(Instant.parse("2026-09-21T10:00:00Z"), ZoneOffset.UTC);
  private final DecisionQueueService service =
      new DecisionQueueService(
          repository, reads, writes, users, eligibility, routing, rebuild, clock);

  @Test
  void inactiveCallerIsRejectedBeforeAnyScopeOrCountQuery() {
    when(users.isActive(tenant, caller)).thenReturn(false);
    assertThatThrownBy(() -> service.list(tenant, caller, DecisionQueueBucket.MINE, 0, 20))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(reads, repository);
  }

  @Test
  void pageSizeDoesNotChangeCrossModuleCallBudget() {
    assertBudget(1);
    reset(repository, reads, writes, users, eligibility, routing);
    assertBudget(100);
    assertThat(
            Arrays.stream(DecisionQueueService.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .toList())
        .doesNotContain(
            com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort.class
                .getName(),
            com.fabricmanagement.sales.salesorder.app.OrderCoverQueryService.class.getName());
  }

  @Test
  void pageOfOneHundredOutsideThePoolSkipsWriteScopeAndQueriesMembershipOnce() {
    arrangeCaller();
    List<DecisionQueueRepository.Row> rows =
        IntStream.range(0, 100).mapToObj(index -> row()).toList();
    when(repository.page(
            any(), any(), anySet(), any(), eq(DecisionQueueBucket.MINE), eq(0), eq(100)))
        .thenReturn(new DecisionQueueRepository.PageSlice(rows, rows.size()));
    when(routing.isActiveMember(tenant, RoutingPoolKey.ORDER_COVER, caller)).thenReturn(false);

    assertThat(service.list(tenant, caller, DecisionQueueBucket.MINE, 0, 100).getContent())
        .hasSize(100);

    verify(routing).isActiveMember(tenant, RoutingPoolKey.ORDER_COVER, caller);
    verifyNoInteractions(writes);
  }

  @Test
  void summaryFailurePropagatesAndNeverBecomesAZero() {
    arrangeCaller();
    when(repository.count(any(), any(), anySet(), any(), eq(DecisionQueueBucket.MINE)))
        .thenThrow(new IllegalStateException("database unavailable"));
    assertThatThrownBy(() -> service.summary(tenant, caller))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("database unavailable");
  }

  @Test
  void unassignedCountIsNullWithoutManageRoutingPermission() {
    arrangeCaller();
    when(repository.count(any(), any(), anySet(), any(), any())).thenReturn(3L);
    var summary = service.summary(tenant, caller);
    assertThat(summary.unassignedCount()).isNull();
    verify(repository, never())
        .count(any(), any(), anySet(), any(), eq(DecisionQueueBucket.UNASSIGNED));
    assertThat(summary.computedAt()).isEqualTo(clock.instant());
  }

  @Test
  void explicitUnassignedRequestReturnsTheTypedMissingPermissionReason() {
    arrangeCaller();

    DecisionBlockedException failure =
        catchThrowableOfType(
            () -> service.list(tenant, caller, DecisionQueueBucket.UNASSIGNED, 0, 20),
            DecisionBlockedException.class);

    assertThat(failure.getDetails()).containsKey("reason");
    assertThat(failure.getDetails().get("reason").toString())
        .contains("PERMISSION_DENIED", "flowboard:manage-routing");
    verifyNoInteractions(repository);
  }

  @Test
  void deniedReadScopeShortCircuitsPagesAndCounts() {
    arrangeCaller();
    when(reads.readScope(tenant, caller)).thenReturn(SalesOrderReadScopePort.OrderReadScope.none());
    when(repository.hasOverduePublication(tenant, DecisionQueueFreshness.LISTENERS))
        .thenReturn(false);

    assertThat(service.list(tenant, caller, DecisionQueueBucket.MINE, 0, 20).getContent())
        .isEmpty();
    var summary = service.summary(tenant, caller);

    assertThat(summary.mineCount()).isZero();
    assertThat(summary.departmentCount()).isZero();
    assertThat(summary.waitingCount()).isZero();
    assertThat(summary.unassignedCount()).isNull();
    verify(repository, never()).page(any(), any(), anySet(), any(), any(), anyInt(), anyInt());
    verify(repository, never()).count(any(), any(), anySet(), any(), any());
  }

  @Test
  void summaryIsStaleWhileARebuildIsRunning() {
    arrangeCaller();
    when(repository.count(any(), any(), anySet(), any(), any())).thenReturn(0L);
    when(repository.hasOverduePublication(tenant, DecisionQueueFreshness.LISTENERS))
        .thenReturn(false);
    rebuild.started(tenant);
    try {
      assertThat(service.summary(tenant, caller).stale()).isTrue();
    } finally {
      rebuild.finished(tenant);
    }
  }

  @Test
  void queueItemUsesTheCanonicalFrontendOrderAndDecisionRoutes() {
    arrangeCaller();
    when(routing.isActiveMember(tenant, RoutingPoolKey.ORDER_COVER, caller)).thenReturn(true);
    DecisionQueueRepository.Row row = row();
    when(repository.page(
            any(), any(), anySet(), any(), eq(DecisionQueueBucket.MINE), eq(0), eq(20)))
        .thenReturn(new DecisionQueueRepository.PageSlice(List.of(row), 1));
    when(writes.allowedOrderIds(tenant, caller, List.of(row.orderId())))
        .thenReturn(Set.of(row.orderId()));

    var item =
        service.list(tenant, caller, DecisionQueueBucket.MINE, 0, 20).getContent().getFirst();

    assertThat(item.subject().accessibleHref()).isEqualTo("/sales/" + row.orderId());
    assertThat(item.detailHref()).isEqualTo("/decisions/order-cover/" + row.orderId());
  }

  @Test
  void freshnessListenersMatchEveryDecisionListenerAndTaskCreationListener() {
    Set<String> derived =
        new ClassFileImporter()
            .importPackages("com.fabricmanagement.flowboard.decision.app.listener").stream()
                .filter(
                    listener ->
                        listener.getMethods().stream()
                            .anyMatch(
                                method -> method.isAnnotatedWith(ApplicationModuleListener.class)))
                .map(listener -> listener.getName())
                .collect(java.util.stream.Collectors.toSet());
    derived.add(OrderCoverCaseOpenedListener.class.getName());

    assertThat(DecisionQueueFreshness.LISTENERS).isEqualTo(derived);
  }

  private void assertBudget(int numberOfRows) {
    arrangeCaller();
    when(routing.isActiveMember(tenant, RoutingPoolKey.ORDER_COVER, caller)).thenReturn(true);
    List<DecisionQueueRepository.Row> rows =
        IntStream.range(0, numberOfRows).mapToObj(index -> row()).toList();
    when(repository.page(
            any(), any(), anySet(), any(), eq(DecisionQueueBucket.MINE), eq(0), eq(numberOfRows)))
        .thenReturn(new DecisionQueueRepository.PageSlice(rows, rows.size()));
    when(writes.allowedOrderIds(eq(tenant), eq(caller), anyCollection()))
        .thenAnswer(call -> Set.copyOf(call.<Collection<UUID>>getArgument(2)));

    assertThat(service.list(tenant, caller, DecisionQueueBucket.MINE, 0, numberOfRows).getContent())
        .hasSize(numberOfRows);
    verify(reads, times(1)).readScope(tenant, caller);
    verify(writes, times(1)).allowedOrderIds(eq(tenant), eq(caller), anyCollection());
    verify(routing, times(1)).isActiveMember(tenant, RoutingPoolKey.ORDER_COVER, caller);
  }

  private void arrangeCaller() {
    when(users.isActive(tenant, caller)).thenReturn(true);
    when(users.departmentIds(tenant, caller)).thenReturn(Set.of());
    when(reads.readScope(tenant, caller)).thenReturn(SalesOrderReadScopePort.OrderReadScope.all());
    var permissions =
        new PermissionResult(
            Map.of(
                "flowboard", Map.of("write", DataScope.OWN),
                "sales", Map.of("write", DataScope.OWN)),
            false);
    var user = new UserPermissions(tenant, caller, "Caller", true, permissions);
    when(eligibility.user(tenant, caller)).thenReturn(user);
    when(eligibility.candidacy(tenant, user)).thenReturn(List.of());
  }

  private DecisionQueueRepository.Row row() {
    return new DecisionQueueRepository.Row(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "SO-1",
        UUID.randomUUID(),
        0,
        "HIGH",
        LocalDate.of(2026, 10, 1),
        clock.instant(),
        clock.instant(),
        "ACTIONABLE",
        List.of(caller),
        List.of());
  }
}
