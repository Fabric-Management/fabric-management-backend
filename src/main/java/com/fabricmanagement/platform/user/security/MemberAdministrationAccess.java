package com.fabricmanagement.platform.user.security;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContextResolver;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.PlatformAuthority;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.Role;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberAdministrationAccess {

  private static final Set<String> PRIVILEGED_ROLE_CODES =
      Set.of("MANAGER", "TRADING_PARTNER_MANAGER", "TENANT_ADMIN", "DEPARTMENT_ADMIN", "HR");

  private final AuthenticatedUserContextResolver contexts;
  private final PermissionIdentityReader identities;
  private final PermissionEvaluator permissions;

  public Actor requireManage() {
    UUID tenantId = TenantContext.requireTenantId();
    var context =
        contexts
            .resolve(SecurityContextHolder.getContext().getAuthentication())
            .filter(actor -> tenantId.equals(actor.tenantId()))
            .orElseThrow(MemberAdministrationAccess::denied);
    var identity =
        identities.find(tenantId, context.userId()).orElseThrow(MemberAdministrationAccess::denied);
    String roleCode =
        PlatformAuthority.effectiveRoleCode(
            identity.roleCode(), tenantId, context.isPlayground(), context.demoImpersonation());
    var scope =
        permissions
            .evaluateFresh(tenantId, roleCode, identity.departmentCodes(), context.userId())
            .scopeOf("members", "manage");
    if (scope == null || scope.compareTo(DataScope.ORGANIZATION) < 0) {
      throw denied();
    }
    return new Actor(context.userId(), roleCode);
  }

  public UUID requireDepartmentChange(UUID userId) {
    var actor = requireManage();
    if (actor.userId().equals(userId) && !"ADMIN".equals(actor.roleCode())) {
      throw new PlatformDomainException(
          "Only an administrator can change their own departments",
          "MEMBERS_SELF_ASSIGNMENT_FORBIDDEN",
          403);
    }
    return actor.userId();
  }

  public void requireRoleGrant(Actor actor, Role role) {
    String code = role.getRoleCode().toUpperCase(Locale.ROOT);
    if (role.isPlatformScope() || code.startsWith("ROLE_")) {
      throw new PlatformDomainException("Role not found", "USER_ROLE_NOT_FOUND", 404);
    }
    if ("ADMIN".equalsIgnoreCase(role.getRoleCode()) && !"ADMIN".equals(actor.roleCode())) {
      throw new PlatformDomainException(
          "Only an administrator can assign the administrator role",
          "MEMBERS_ADMIN_GRANT_REQUIRES_ADMIN",
          403);
    }
    if (!"MANAGER".equals(code)
        && PRIVILEGED_ROLE_CODES.contains(code)
        && !"ADMIN".equals(actor.roleCode())) {
      throw new PlatformDomainException(
          "Only an administrator can assign this role", "MEMBERS_PRIVILEGED_ROLE_GRANT", 403);
    }
  }

  public void requireRoleCreation(Actor actor, String roleCode) {
    String code = roleCode.toUpperCase(Locale.ROOT);
    if (code.startsWith("ROLE_") || "ADMIN".equals(code)) {
      throw new PlatformDomainException("Role code is reserved", "ROLE_CODE_RESERVED", 409);
    }
    if (PRIVILEGED_ROLE_CODES.contains(code) && !"ADMIN".equals(actor.roleCode())) {
      throw new PlatformDomainException("Role code is reserved", "ROLE_CODE_RESERVED", 403);
    }
  }

  public record Actor(UUID userId, String roleCode) {}

  private static PlatformDomainException denied() {
    return new PlatformDomainException(
        "Organisation-wide member administration authority is required",
        "MEMBERS_MANAGE_REQUIRED",
        403);
  }
}
