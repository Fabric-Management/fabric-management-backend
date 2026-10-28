package com.fabricmanagement.flowboard.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
import com.fabricmanagement.flowboard.decision.app.*;
import com.fabricmanagement.flowboard.decision.domain.DecisionQueueBucket;
import com.fabricmanagement.flowboard.decision.dto.DecisionQueueItem;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionQueueRepository;
import com.fabricmanagement.flowboard.routing.app.RoutingEligibilityService;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.port.out.*;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.salesorder.domain.port.SalesOrderReadScopePort;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;

class CollectionDecisionQueueTest {
  @Test
  void aFinanceOnlyPageOfOneHundredDoesNotQuerySalesOrAuthorisationPerRow() {
    var tenant = UUID.randomUUID();
    var actor = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    try {
      var repository = mock(DecisionQueueRepository.class);
      var reads = mock(SalesOrderReadScopePort.class);
      var writes = mock(SalesOrderWriteScopePort.class);
      var users = mock(UserFacade.class);
      var eligibility = mock(RoutingEligibilityService.class);
      var routing = mock(RoutingRepository.class);
      var source = mock(CollectionFollowUpPort.class);
      var clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
      var service =
          new DecisionQueueService(
              repository,
              reads,
              writes,
              users,
              eligibility,
              routing,
              new DecisionProjectionRebuildState(),
              clock,
              source,
              mock(CollectionFollowUpHealthService.class));
      var user =
          new RoutingUserQueryPort.UserPermissions(
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
                  false));
      when(users.isActive(tenant, actor)).thenReturn(true);
      when(users.departmentIds(tenant, actor)).thenReturn(Set.of());
      when(reads.readScope(tenant, actor))
          .thenReturn(SalesOrderReadScopePort.OrderReadScope.none());
      when(eligibility.user(tenant, actor)).thenReturn(user);
      when(eligibility.candidacy(tenant, user))
          .thenReturn(List.of(RoutingReason.MISSING_SALES_WRITE));
      when(eligibility.candidacy(tenant, user, RoutingPoolKey.COLLECTION_FOLLOW_UP))
          .thenReturn(List.of());
      when(source.readScope(actor))
          .thenReturn(new CollectionFollowUpPort.ReadScope(false, Set.of(actor)));
      when(routing.isActiveMember(tenant, RoutingPoolKey.COLLECTION_FOLLOW_UP, actor))
          .thenReturn(true);
      var rows =
          IntStream.range(0, 100)
              .mapToObj(
                  i ->
                      new DecisionQueueRepository.Row(
                          UUID.randomUUID(),
                          UUID.randomUUID(),
                          "Northbridge Cloth · GBP 4284.00",
                          UUID.randomUUID(),
                          2,
                          "MEDIUM",
                          LocalDate.of(2026, 10, 20),
                          clock.instant(),
                          clock.instant(),
                          "ACTIONABLE",
                          List.of(actor),
                          List.of(),
                          "COLLECTION_FOLLOW_UP"))
              .toList();
      when(repository.page(
              eq(tenant),
              eq(actor),
              anySet(),
              any(),
              eq(DecisionQueueBucket.MINE),
              eq(0),
              eq(100),
              any()))
          .thenReturn(new DecisionQueueRepository.PageSlice(rows, 100));
      var result = service.list(tenant, actor, DecisionQueueBucket.MINE, 0, 100);
      assertThat(result.getContent())
          .hasSize(100)
          .allMatch(
              item ->
                  item.kind() == DecisionQueueItem.Kind.COLLECTION_FOLLOW_UP
                      && item.subject().accessibleHref() == null
                      && item.actions().getFirst().allowed());
      verifyNoInteractions(writes);
      verify(eligibility, times(2)).user(tenant, actor);
      verify(routing).isActiveMember(tenant, RoutingPoolKey.COLLECTION_FOLLOW_UP, actor);
      verify(source).readScope(actor);
    } finally {
      TenantContext.clear();
    }
  }
}
