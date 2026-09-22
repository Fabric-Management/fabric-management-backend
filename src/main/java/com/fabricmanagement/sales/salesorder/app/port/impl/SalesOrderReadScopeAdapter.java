package com.fabricmanagement.sales.salesorder.app.port.impl;

import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.PermissionFreshness;
import com.fabricmanagement.sales.salesorder.domain.port.SalesOrderReadScopePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the same cached sales-read scope used by SalesOrderAccessPolicy.readRestriction. */
@Component
@RequiredArgsConstructor
public class SalesOrderReadScopeAdapter implements SalesOrderReadScopePort {
  private final SalesAccessScopeResolver scopes;

  @Override
  public OrderReadScope readScope(java.util.UUID tenantId, java.util.UUID callerId) {
    var scope = scopes.resolve(tenantId, callerId, "read", PermissionFreshness.CACHED);
    if (scope.scope() == null) return OrderReadScope.none();
    return switch (scope.scope()) {
      case GLOBAL, ORGANIZATION -> OrderReadScope.all();
      case OWN -> OrderReadScope.principals(java.util.Set.of(callerId));
      case DEPARTMENT -> OrderReadScope.principals(scope.permittedPrincipalIds());
    };
  }
}
