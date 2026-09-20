package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.routing.app.RoutingEligibilityService;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.RoutingRecords.*;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.TaskAssigneeRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.InOrder;

class OrderCoverAuthorisationTest {
  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void locksPoolBeforeRejectingRemovedMember() {
    UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    RoutingRepository routing = mock(RoutingRepository.class);
    RoutingEligibilityService eligibility = mock(RoutingEligibilityService.class);
    TaskAssigneeRepository assignees = mock(TaskAssigneeRepository.class);
    Task task = mock(Task.class);
    when(task.getEntityId()).thenReturn(UUID.randomUUID());
    Pool pool = new Pool(UUID.randomUUID(), RoutingPoolKey.ORDER_COVER, 2);
    when(routing.pool(tenant, RoutingPoolKey.ORDER_COVER)).thenReturn(Optional.of(pool));
    when(routing.members(tenant, pool.id())).thenReturn(List.of());
    var user =
        new com.fabricmanagement.flowboard.routing.domain.port.out.RoutingUserQueryPort
            .UserPermissions(
            tenant,
            actor,
            "Actor",
            true,
            new com.fabricmanagement.common.infrastructure.security.dto.PermissionResult(
                Map.of(), false));
    when(eligibility.user(tenant, actor)).thenReturn(user);
    when(eligibility.eligibility(tenant, task.getEntityId(), user, false))
        .thenReturn(List.of(RoutingReason.NOT_A_MEMBER));
    assertThatThrownBy(
            () ->
                new OrderCoverAuthorisation(routing, eligibility, assignees)
                    .assertFirstExecution(task, actor))
        .isInstanceOf(DecisionBlockedException.class)
        .hasMessageContaining("not currently allowed");
    InOrder order = inOrder(routing, eligibility);
    order.verify(routing).lockPool(tenant, RoutingPoolKey.ORDER_COVER, false);
    order.verify(eligibility).user(tenant, actor);
  }
}
