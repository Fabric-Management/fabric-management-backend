package com.fabricmanagement.sales.salesorder.app.port.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.AccessScope;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.PermissionFreshness;
import com.fabricmanagement.sales.salesorder.domain.port.SalesOrderReadScopePort.Kind;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SalesOrderReadScopeAdapterTest {
  private final UUID tenant = UUID.randomUUID();
  private final UUID caller = UUID.randomUUID();
  private final SalesAccessScopeResolver resolver = mock(SalesAccessScopeResolver.class);
  private final SalesOrderReadScopeAdapter adapter = new SalesOrderReadScopeAdapter(resolver);

  @Test
  void translatesTheSameCachedReadScopesAsSalesOrderAccessPolicy() {
    UUID departmentMember = UUID.randomUUID();
    assertScope(AccessScope.denied(), Kind.NONE, Set.of());
    assertScope(new AccessScope(DataScope.OWN, Set.of(caller)), Kind.PRINCIPALS, Set.of(caller));
    assertScope(
        new AccessScope(DataScope.DEPARTMENT, Set.of(caller, departmentMember)),
        Kind.PRINCIPALS,
        Set.of(caller, departmentMember));
    assertScope(new AccessScope(DataScope.ORGANIZATION, Set.of()), Kind.ALL, Set.of());
    assertScope(new AccessScope(DataScope.GLOBAL, Set.of()), Kind.ALL, Set.of());
  }

  @Test
  void emptyDepartmentPrincipalsDenyInsteadOfBecomingAll() {
    assertScope(new AccessScope(DataScope.DEPARTMENT, Set.of()), Kind.NONE, Set.of());
  }

  private void assertScope(AccessScope source, Kind kind, Set<UUID> principals) {
    reset(resolver);
    when(resolver.resolve(tenant, caller, "read", PermissionFreshness.CACHED)).thenReturn(source);
    var result = adapter.readScope(tenant, caller);
    assertThat(result.kind()).isEqualTo(kind);
    assertThat(result.principalIds()).isEqualTo(principals);
    verify(resolver).resolve(tenant, caller, "read", PermissionFreshness.CACHED);
  }
}
