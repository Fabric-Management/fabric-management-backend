package com.fabricmanagement.common.infrastructure.security;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.infrastructure.tenant.PackageAccessPort;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The single door every {@code @PreAuthorize("@auth.can(...)")} endpoint goes through.
 *
 * <p>Access needs two independent yeses: the user's role/department permission, and the tenant's
 * packages ({@link PackageAccessPort}). The package check is not a permission and is not bypassed
 * by an administrator role; only platform staff operating across tenants skip it.
 */
@Component("auth")
public class SpELPermissionEvaluator {

  private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

  private final PermissionEvaluator permissionEvaluator;
  private final AuthenticatedUserContextResolver contextResolver;
  private final Optional<PackageAccessPort> packages;

  @Autowired
  public SpELPermissionEvaluator(
      PermissionEvaluator permissionEvaluator,
      AuthenticatedUserContextResolver contextResolver,
      Optional<PackageAccessPort> packages) {
    this.permissionEvaluator = permissionEvaluator;
    this.contextResolver = contextResolver;
    this.packages = packages;
  }

  /** Role-only evaluator for slices that have no package catalogue (every resource stays open). */
  public SpELPermissionEvaluator(
      PermissionEvaluator permissionEvaluator, AuthenticatedUserContextResolver contextResolver) {
    this(permissionEvaluator, contextResolver, Optional.empty());
  }

  /** Used in SpEL expressions: @PreAuthorize("@auth.can(authentication, 'sales', 'write')") */
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

    return result.can(resource, action) && packageOpens(ctx, resource);
  }

  /**
   * Data scope control: @PreAuthorize("@auth.hasScope(authentication, 'sales', 'read',
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

    return userScope.compareTo(reqScope) >= 0 && packageOpens(ctx, resource);
  }

  private boolean packageOpens(AuthenticatedUserContext ctx, String resource) {
    if (PLATFORM_ADMIN.equals(ctx.roleCode())) {
      return true;
    }
    boolean open = packages.map(port -> port.isResourceOpen(ctx.tenantId(), resource)).orElse(true);
    if (!open) {
      PackageAccessDenial.record(resource);
    }
    return open;
  }
}
