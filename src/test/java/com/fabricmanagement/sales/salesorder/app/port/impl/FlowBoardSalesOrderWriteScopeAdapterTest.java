package com.fabricmanagement.sales.salesorder.app.port.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowBoardSalesOrderWriteScopeAdapterTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID OTHER_TENANT = UUID.randomUUID();
  private static final UUID TARGET = UUID.randomUUID();
  private static final UUID COLLEAGUE = UUID.randomUUID();
  private static final UUID OUTSIDER = UUID.randomUUID();

  @Mock private SalesOrderRepository orders;
  @Mock private PermissionEvaluator evaluator;
  @Mock private UserQueryService users;

  private FlowBoardSalesOrderWriteScopeAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter =
        new FlowBoardSalesOrderWriteScopeAdapter(
            orders, new SalesOrderAccessPolicy(evaluator, users));
    lenient()
        .when(users.findPermissionIdentity(TENANT, TARGET))
        .thenReturn(Optional.of(new PermissionIdentity("WORKER", List.of("SALES"))));
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void ownScopeAllowsOwnOrderAndDeniesAnotherUsersOrder() {
    freshScope(DataScope.OWN);

    assertThat(evaluate(order(TENANT, TARGET))).isTrue();
    assertThat(evaluate(order(TENANT, OUTSIDER))).isFalse();
  }

  @Test
  void departmentScopeAllowsMembersAndDeniesOutsiders() {
    freshScope(DataScope.DEPARTMENT);
    when(users.findActiveUserIdsByDepartmentCodes(TENANT, Set.of("SALES")))
        .thenReturn(Set.of(COLLEAGUE));

    assertThat(evaluate(order(TENANT, TARGET))).isTrue();
    assertThat(evaluate(order(TENANT, COLLEAGUE))).isTrue();
    assertThat(evaluate(order(TENANT, OUTSIDER))).isFalse();
  }

  @Test
  void ownAndDepartmentDenyUnknownCreator() {
    SalesOrder order = order(TENANT, null);
    when(orders.findByTenantIdAndId(TENANT, order.getId())).thenReturn(Optional.of(order));
    freshScope(DataScope.OWN);
    assertThat(adapter.isAllowed(TENANT, TARGET, order.getId())).isFalse();

    freshScope(DataScope.DEPARTMENT);
    when(users.findActiveUserIdsByDepartmentCodes(TENANT, Set.of("SALES")))
        .thenReturn(Set.of(COLLEAGUE));
    assertThat(adapter.isAllowed(TENANT, TARGET, order.getId())).isFalse();
  }

  @Test
  void missingAndOtherTenantOrdersAreNotAllowed() {
    UUID missing = UUID.randomUUID();
    UUID otherTenantOrder = UUID.randomUUID();
    when(orders.findByTenantIdAndId(TENANT, missing)).thenReturn(Optional.empty());
    when(orders.findByTenantIdAndId(TENANT, otherTenantOrder)).thenReturn(Optional.empty());

    assertThat(adapter.isAllowed(TENANT, TARGET, missing)).isFalse();
    assertThat(adapter.isAllowed(TENANT, TARGET, otherTenantOrder)).isFalse();
  }

  @Test
  void systemContextConfersNothingOnExplicitTargetUser() {
    TenantContext.setCurrentTenantId(TENANT);
    TenantContext.setCurrentUserId(SystemUser.ID);
    freshScope(DataScope.OWN);

    assertThat(evaluate(order(TENANT, OUTSIDER))).isFalse();
  }

  @Test
  void tenantBoundaryIsStillAppliedByTheSalesPolicy() {
    SalesOrder otherTenantOrder = order(OTHER_TENANT, TARGET);
    when(orders.findByTenantIdAndId(TENANT, otherTenantOrder.getId()))
        .thenReturn(Optional.of(otherTenantOrder));

    assertThat(adapter.isAllowed(TENANT, TARGET, otherTenantOrder.getId())).isFalse();
  }

  private boolean evaluate(SalesOrder order) {
    when(orders.findByTenantIdAndId(TENANT, order.getId())).thenReturn(Optional.of(order));
    return adapter.isAllowed(TENANT, TARGET, order.getId());
  }

  private void freshScope(DataScope scope) {
    when(evaluator.evaluateFresh(TENANT, "WORKER", List.of("SALES"), TARGET))
        .thenReturn(new PermissionResult(Map.of("sales", Map.of("write", scope)), false));
  }

  private SalesOrder order(UUID tenantId, UUID creatorId) {
    SalesOrder order = SalesOrder.builder().build();
    order.setId(UUID.randomUUID());
    order.setTenantId(tenantId);
    order.setCreatedBy(creatorId);
    return order;
  }
}
