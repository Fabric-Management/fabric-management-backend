package com.fabricmanagement.sales.common.app;

import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The permissions and departments of a given user, for work that is scoped by the team and person
 * responsible for it rather than by the sales owner of the order.
 */
@Component
@RequiredArgsConstructor
public class WorkScopeResolver {

  private final PermissionEvaluator permissionEvaluator;
  private final UserQueryService userQueryService;

  /** Fresh permissions for a mutation; the cached ones for lists and views. */
  public WorkActor actor(UUID tenantId, UUID userId, boolean fresh) {
    if (tenantId == null || userId == null) {
      return WorkActor.nobody(userId);
    }
    PermissionIdentity identity =
        userQueryService.findPermissionIdentity(tenantId, userId).orElse(null);
    if (identity == null) {
      return WorkActor.nobody(userId);
    }
    PermissionResult permissions =
        fresh
            ? permissionEvaluator.evaluateFresh(
                tenantId, identity.roleCode(), identity.departmentCodes(), userId)
            : permissionEvaluator.evaluate(
                tenantId, identity.roleCode(), identity.departmentCodes(), userId);
    return new WorkActor(
        userId,
        identity.departmentCodes().stream()
            .map(code -> code.toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet()),
        permissions);
  }

  /** A user with their departments and effective permissions. */
  public record WorkActor(UUID userId, Set<String> departments, PermissionResult permissions) {

    static WorkActor nobody(UUID userId) {
      return new WorkActor(userId, Set.of(), null);
    }

    /** The scope of the permission, or null when the user does not hold it. */
    public DataScope scope(PermissionKey key) {
      return permissions == null ? null : permissions.scopeOf(key.resource(), key.action());
    }

    public boolean inDepartment(String departmentCode) {
      return departmentCode != null
          && departments.contains(departmentCode.toUpperCase(Locale.ROOT));
    }
  }
}
