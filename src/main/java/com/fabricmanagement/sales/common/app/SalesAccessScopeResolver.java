package com.fabricmanagement.sales.common.app;

import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves sales permission scope for an explicit target user. */
@Component
@RequiredArgsConstructor
public class SalesAccessScopeResolver {

  private static final String RESOURCE = "sales";

  private final PermissionEvaluator permissionEvaluator;
  private final UserQueryService userQueryService;

  public AccessScope resolve(
      UUID tenantId, UUID userId, String action, PermissionFreshness freshness) {
    if (tenantId == null || userId == null || action == null) {
      return AccessScope.denied();
    }

    PermissionIdentity targetUser =
        userQueryService.findPermissionIdentity(tenantId, userId).orElse(null);
    if (targetUser == null) {
      return AccessScope.denied();
    }

    PermissionResult permissions =
        freshness == PermissionFreshness.FRESH
            ? permissionEvaluator.evaluateFresh(
                tenantId, targetUser.roleCode(), targetUser.departmentCodes(), userId)
            : permissionEvaluator.evaluate(
                tenantId, targetUser.roleCode(), targetUser.departmentCodes(), userId);
    DataScope scope = permissions.scopeOf(RESOURCE, action);
    if (scope == null) {
      return AccessScope.denied();
    }
    if (scope == DataScope.OWN) {
      return new AccessScope(scope, Set.of(userId));
    }
    if (scope != DataScope.DEPARTMENT) {
      return new AccessScope(scope, Set.of());
    }

    Set<UUID> memberIds = new HashSet<>();
    if (!targetUser.departmentCodes().isEmpty()) {
      memberIds.addAll(
          userQueryService.findActiveUserIdsByDepartmentCodes(
              tenantId,
              targetUser.departmentCodes().stream()
                  .map(code -> code.toUpperCase(Locale.ROOT))
                  .collect(Collectors.toUnmodifiableSet())));
    }
    memberIds.add(userId);
    return new AccessScope(scope, Set.copyOf(memberIds));
  }

  public enum PermissionFreshness {
    CACHED,
    FRESH
  }

  public record AccessScope(DataScope scope, Set<UUID> permittedPrincipalIds) {

    public static AccessScope denied() {
      return new AccessScope(null, Set.of());
    }

    public boolean permits(UUID objectTenantId, UUID tenantId, Set<UUID> principals) {
      if (scope == null || tenantId == null || !tenantId.equals(objectTenantId)) {
        return false;
      }
      if (scope == DataScope.ORGANIZATION || scope == DataScope.GLOBAL) {
        return true;
      }
      return principals != null
          && principals.stream()
              .filter(java.util.Objects::nonNull)
              .anyMatch(permittedPrincipalIds::contains);
    }
  }
}
