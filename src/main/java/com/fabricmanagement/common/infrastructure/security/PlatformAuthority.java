package com.fabricmanagement.common.infrastructure.security;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.UUID;

/**
 * The one rule for platform authority (PLATFORM-BOUNDARY-1).
 *
 * <p>Platform staff are Longward's people: the {@code PLATFORM_ADMIN} role held by a user of the
 * platform tenant, acting on a real session. A role code alone grants nothing. The same code in a
 * customer, demo or Playground tenant, or on a Playground or demo-persona token, acts as no role at
 * all, so a role that was copied, created or assigned by mistake can never open the platform.
 */
public final class PlatformAuthority {

  /** The platform staff role code. It exists only in the platform tenant. */
  public static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

  private PlatformAuthority() {}

  /**
   * Whether the code names the platform staff role (case-insensitive). Mirrored by {@code
   * Role.isPlatformRoleCode} for the domain layer, which may not depend on this class.
   */
  public static boolean isPlatformRoleCode(String roleCode) {
    return PLATFORM_ADMIN_ROLE.equalsIgnoreCase(roleCode);
  }

  /** Whether the tenant is the platform (Longward) tenant. */
  public static boolean isPlatformTenant(UUID tenantId) {
    return TenantContext.SYSTEM_TENANT_ID.equals(tenantId);
  }

  /** Platform staff: the platform role, held in the platform tenant. */
  public static boolean isPlatformAdmin(String roleCode, UUID tenantId) {
    return isPlatformRoleCode(roleCode) && isPlatformTenant(tenantId);
  }

  /**
   * Platform staff on this authenticated session (never a Playground session). A demo-persona token
   * is not visible here: {@link JwtAuthenticationFilter} has already removed the platform role from
   * it through {@link #effectiveRoleCode}, so the context never carries it.
   */
  public static boolean isPlatformAdmin(AuthenticatedUserContext context) {
    return context != null
        && !context.isPlayground()
        && isPlatformAdmin(context.roleCode(), context.tenantId());
  }

  /**
   * The role a token may act with. Every role but the platform role passes unchanged; the platform
   * role passes only in the platform tenant on a real session, and otherwise becomes no role.
   *
   * @param roleCode the token's role code
   * @param tenantId the token's tenant
   * @param playgroundToken whether the token is an anonymous Playground token
   * @param personaToken whether the token is a demo-persona token
   * @return the role code to act with, or {@code null} for none
   */
  public static String effectiveRoleCode(
      String roleCode, UUID tenantId, boolean playgroundToken, boolean personaToken) {
    if (!isPlatformRoleCode(roleCode)) {
      return roleCode;
    }
    return isPlatformTenant(tenantId) && !playgroundToken && !personaToken ? roleCode : null;
  }
}
