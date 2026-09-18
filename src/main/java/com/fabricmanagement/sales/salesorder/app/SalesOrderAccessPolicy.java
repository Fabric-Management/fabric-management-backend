package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesOrderAccessPolicy {

  private static final String RESOURCE = "sales";

  private final PermissionEvaluator permissionEvaluator;
  private final UserQueryService userQueryService;

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
    AccessScope accessScope =
        resolveAccessScope(tenantId, userId, "read", PermissionFreshness.CACHED);
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
                accessScope.departmentMemberIds().isEmpty()
                    ? criteriaBuilder.disjunction()
                    : root.get("createdBy").in(accessScope.departmentMemberIds());
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
    if (accessScope.scope() == null) {
      return false;
    }
    return switch (accessScope.scope()) {
      case GLOBAL, ORGANIZATION -> true;
      case OWN -> userId.equals(order.getCreatedBy());
      case DEPARTMENT ->
          order.getCreatedBy() != null
              && accessScope.departmentMemberIds().contains(order.getCreatedBy());
    };
  }

  private AccessScope resolveAccessScope(
      UUID tenantId, UUID userId, String action, PermissionFreshness freshness) {
    if (tenantId == null || userId == null) {
      return AccessScope.denied();
    }

    PermissionIdentity targetUser =
        userQueryService.findPermissionIdentity(tenantId, userId).orElse(null);
    if (targetUser == null) {
      return AccessScope.denied();
    }

    PermissionResult permissions =
        resolvePermissions(
            tenantId, userId, targetUser.roleCode(), targetUser.departmentCodes(), freshness);
    DataScope scope = permissions.scopeOf(RESOURCE, action);

    if (scope != DataScope.DEPARTMENT) {
      return new AccessScope(scope, Set.of());
    }

    Set<UUID> memberIds = new HashSet<>();
    if (!targetUser.departmentCodes().isEmpty()) {
      memberIds.addAll(
          userQueryService.findActiveUserIdsByDepartmentCodes(
              tenantId,
              targetUser.departmentCodes().stream()
                  .map(code -> code.toUpperCase(java.util.Locale.ROOT))
                  .collect(java.util.stream.Collectors.toUnmodifiableSet())));
    }
    memberIds.add(userId);
    return new AccessScope(scope, Set.copyOf(memberIds));
  }

  private PermissionResult resolvePermissions(
      UUID tenantId,
      UUID userId,
      String roleCode,
      List<String> departmentCodes,
      PermissionFreshness freshness) {
    return switch (freshness) {
      case CACHED -> permissionEvaluator.evaluate(tenantId, roleCode, departmentCodes, userId);
      case FRESH -> permissionEvaluator.evaluateFresh(tenantId, roleCode, departmentCodes, userId);
    };
  }

  public enum PermissionFreshness {
    CACHED,
    FRESH
  }

  private record AccessScope(DataScope scope, Set<UUID> departmentMemberIds) {
    private static AccessScope denied() {
      return new AccessScope(null, Set.of());
    }
  }
}
