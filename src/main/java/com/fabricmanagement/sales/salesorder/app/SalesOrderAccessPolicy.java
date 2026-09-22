package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.AccessScope;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesOrderAccessPolicy {

  private final SalesAccessScopeResolver scopeResolver;

  public boolean canRead(UUID tenantId, UUID userId, SalesOrder order) {
    return canAccess(tenantId, userId, order, "read", PermissionFreshness.CACHED);
  }

  public boolean canWrite(UUID tenantId, UUID userId, SalesOrder order) {
    return canWrite(tenantId, userId, order, PermissionFreshness.CACHED);
  }

  public boolean canWrite(
      UUID tenantId, UUID userId, SalesOrder order, PermissionFreshness freshness) {
    return canAccess(tenantId, userId, order, "write", freshness);
  }

  public Specification<SalesOrder> readRestriction(UUID tenantId, UUID userId) {
    return restriction(tenantId, userId, "read", PermissionFreshness.CACHED);
  }

  public Specification<SalesOrder> writeRestriction(
      UUID tenantId, UUID userId, PermissionFreshness freshness) {
    return restriction(tenantId, userId, "write", freshness);
  }

  private Specification<SalesOrder> restriction(
      UUID tenantId, UUID userId, String action, PermissionFreshness freshness) {
    AccessScope accessScope = resolveAccessScope(tenantId, userId, action, freshness);
    return (root, query, criteriaBuilder) -> {
      var tenantPredicate = criteriaBuilder.equal(root.get("tenantId"), tenantId);
      if (accessScope.scope() == null) {
        return criteriaBuilder.and(tenantPredicate, criteriaBuilder.disjunction());
      }

      var scopePredicate =
          switch (accessScope.scope()) {
            case GLOBAL, ORGANIZATION -> criteriaBuilder.conjunction();
            case OWN -> criteriaBuilder.equal(root.get("createdBy"), userId);
            case DEPARTMENT ->
                accessScope.permittedPrincipalIds().isEmpty()
                    ? criteriaBuilder.disjunction()
                    : root.get("createdBy").in(accessScope.permittedPrincipalIds());
          };
      return criteriaBuilder.and(tenantPredicate, scopePredicate);
    };
  }

  private boolean canAccess(
      UUID tenantId, UUID userId, SalesOrder order, String action, PermissionFreshness freshness) {
    if (tenantId == null
        || userId == null
        || order == null
        || !tenantId.equals(order.getTenantId())) {
      return false;
    }

    AccessScope accessScope = resolveAccessScope(tenantId, userId, action, freshness);
    Set<UUID> principals = order.getCreatedBy() == null ? Set.of() : Set.of(order.getCreatedBy());
    return accessScope.permits(order.getTenantId(), tenantId, principals);
  }

  private AccessScope resolveAccessScope(
      UUID tenantId, UUID userId, String action, PermissionFreshness freshness) {
    return scopeResolver.resolve(
        tenantId,
        userId,
        action,
        freshness == PermissionFreshness.FRESH
            ? SalesAccessScopeResolver.PermissionFreshness.FRESH
            : SalesAccessScopeResolver.PermissionFreshness.CACHED);
  }

  public enum PermissionFreshness {
    CACHED,
    FRESH
  }
}
