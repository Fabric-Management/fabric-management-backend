package com.fabricmanagement.finance.collection.security;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.tenant.CurrentTenantAccessPort;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Finance scope is independent of sales scope. Projection creation by a system actor grants nobody
 * authority.
 */
@Component
@RequiredArgsConstructor
public class CollectionAccessService {
  private final UserQueryService users;
  private final PermissionEvaluator permissions;
  private final CurrentTenantAccessPort tenants;

  public Reach reach(UUID actor, String action) {
    UUID tenant = TenantContext.requireTenantId();
    if (!tenants.currentTenantHasAccess() || actor == null || !users.isActive(tenant, actor))
      throw new AccessDeniedException("Active finance access is required");
    var identity =
        users
            .findPermissionIdentity(tenant, actor)
            .orElseThrow(() -> new AccessDeniedException("Active finance access is required"));
    var scope =
        permissions
            .evaluateFresh(tenant, identity.roleCode(), identity.departmentCodes(), actor)
            .scopeOf("finance", action);
    var principals = new HashSet<UUID>();
    principals.add(actor);
    if (scope == DataScope.DEPARTMENT)
      principals.addAll(
          users.findActiveUserIdsByDepartmentCodes(tenant, Set.copyOf(identity.departmentCodes())));
    return new Reach(tenant, scope, Set.copyOf(principals));
  }

  /**
   * Refuses an actor who holds no grant for the action at all. Scope checks answer "not found" for
   * rows out of reach; a missing grant is a different answer and must not look like a missing row.
   */
  public void requireGrant(UUID actor, String action) {
    if (reach(actor, action).scope() == null)
      throw new AccessDeniedException("Missing finance permission: " + action);
  }

  public void require(UUID actor, String action, CollectionObligation row) {
    if (!reach(actor, action)
        .permits(row.getTenantId(), row.getCreatedBy(), row.getResponsibleUserId()))
      throw new NotFoundException("Collection obligation not found");
  }

  public record Reach(UUID tenantId, DataScope scope, Set<UUID> principals) {
    public boolean permits(UUID tenant, UUID creator, UUID owner) {
      return tenantId.equals(tenant)
          && scope != null
          && (scope == DataScope.GLOBAL
              || scope == DataScope.ORGANIZATION
              || (creator != null && principals.contains(creator))
              || (owner != null && principals.contains(owner)));
    }

    public Specification<CollectionObligation> restriction() {
      return (root, query, cb) ->
          cb.and(
              cb.equal(root.get("tenantId"), tenantId),
              scope == null
                  ? cb.disjunction()
                  : scope == DataScope.GLOBAL || scope == DataScope.ORGANIZATION
                      ? cb.conjunction()
                      : cb.or(
                          root.get("createdBy").in(principals),
                          root.get("responsibleUserId").in(principals)));
    }
  }
}
