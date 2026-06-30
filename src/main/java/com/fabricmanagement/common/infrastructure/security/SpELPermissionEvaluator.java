package com.fabricmanagement.common.infrastructure.security;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("auth")
@RequiredArgsConstructor
public class SpELPermissionEvaluator {

  private final PermissionEvaluator permissionEvaluator;
  private final AuthenticatedUserContextResolver contextResolver;

  /** Used in SpEL expressions: @PreAuthorize("@auth.can(authentication, 'SALES', 'WRITE')") */
  public boolean can(Authentication authentication, String resource, String action) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }

    AuthenticatedUserContext ctx = contextResolver.resolve(authentication).orElse(null);
    if (ctx == null) {
      return false;
    }

    PermissionResult result =
        permissionEvaluator.evaluate(
            ctx.tenantId(), ctx.roleCode(), ctx.departmentCodes(), ctx.userId());

    return result.can(resource, action);
  }

  /**
   * Data scope control: @PreAuthorize("@auth.hasScope(authentication, 'SALES', 'READ',
   * 'ORGANIZATION')")
   */
  public boolean hasScope(
      Authentication authentication, String resource, String action, String requiredScope) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }

    AuthenticatedUserContext ctx = contextResolver.resolve(authentication).orElse(null);
    if (ctx == null) {
      return false;
    }

    PermissionResult result =
        permissionEvaluator.evaluate(
            ctx.tenantId(), ctx.roleCode(), ctx.departmentCodes(), ctx.userId());

    com.fabricmanagement.platform.user.domain.DataScope userScope =
        result.scopeOf(resource, action);
    if (userScope == null) {
      return false;
    }

    com.fabricmanagement.platform.user.domain.DataScope reqScope =
        com.fabricmanagement.platform.user.domain.DataScope.valueOf(requiredScope.toUpperCase());

    return userScope.compareTo(reqScope) >= 0;
  }
}
