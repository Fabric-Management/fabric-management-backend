package com.fabricmanagement.finance.collection.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.infrastructure.tenant.CurrentTenantAccessPort;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CollectionAccessServiceTest {
  @Test
  void financeOnlyUserRequiresNoSalesGrantAndRevocationIsReadFresh() {
    UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    try {
      var users = mock(UserQueryService.class);
      var evaluator = mock(PermissionEvaluator.class);
      var tenants = mock(CurrentTenantAccessPort.class);
      when(tenants.currentTenantHasAccess()).thenReturn(true);
      when(users.isActive(tenant, actor)).thenReturn(true);
      when(users.findPermissionIdentity(tenant, actor))
          .thenReturn(
              Optional.of(new UserQueryService.PermissionIdentity("EMPLOYEE", List.of("FINANCE"))));
      when(evaluator.evaluateFresh(tenant, "EMPLOYEE", List.of("FINANCE"), actor))
          .thenReturn(
              new PermissionResult(
                  Map.of("finance", Map.of("read", DataScope.ORGANIZATION)), false));
      var service = new CollectionAccessService(users, evaluator, tenants);
      assertThat(service.reach(actor, "read").permits(tenant, null, null)).isTrue();
      assertThat(service.reach(actor, "write").permits(tenant, actor, actor)).isFalse();
      assertThat(service.reach(actor, "read").permits(UUID.randomUUID(), actor, actor)).isFalse();
      when(users.isActive(tenant, actor)).thenReturn(false);
      assertThatThrownBy(() -> service.reach(actor, "read"))
          .isInstanceOf(AccessDeniedException.class);
      verify(evaluator, never()).evaluate(any(), any(), any(), any());
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void aMissingGrantIsPermissionDeniedWhileAGrantOfAnyScopePasses() {
    UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    try {
      var users = mock(UserQueryService.class);
      var evaluator = mock(PermissionEvaluator.class);
      var tenants = mock(CurrentTenantAccessPort.class);
      when(tenants.currentTenantHasAccess()).thenReturn(true);
      when(users.isActive(tenant, actor)).thenReturn(true);
      when(users.findPermissionIdentity(tenant, actor))
          .thenReturn(
              Optional.of(new UserQueryService.PermissionIdentity("EMPLOYEE", List.of("FINANCE"))));
      when(evaluator.evaluateFresh(tenant, "EMPLOYEE", List.of("FINANCE"), actor))
          .thenReturn(
              new PermissionResult(Map.of("finance", Map.of("write", DataScope.OWN)), false));
      var service = new CollectionAccessService(users, evaluator, tenants);

      assertThatCode(() -> service.requireGrant(actor, "write")).doesNotThrowAnyException();
      assertThatThrownBy(() -> service.requireGrant(actor, "reverse"))
          .isInstanceOf(AccessDeniedException.class)
          .hasMessageContaining("reverse");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void ownScopeUsesFinanceOwnershipAndDoesNotPromoteSalesOwnershipOrNulls() {
    UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
    var reach = new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor));
    assertThat(reach.permits(tenant, null, null)).isFalse();
    assertThat(reach.permits(tenant, null, actor)).isTrue();
    assertThat(reach.permits(tenant, UUID.randomUUID(), UUID.randomUUID())).isFalse();
  }
}
