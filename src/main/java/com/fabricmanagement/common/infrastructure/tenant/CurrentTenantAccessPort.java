package com.fabricmanagement.common.infrastructure.tenant;

/**
 * Whether the tenant bound to the current transaction may use the platform now (CEDIT-05 R2).
 *
 * <p>Unlike {@link TenantAccessPort}, which answers for any tenant id through the system role and a
 * cache, this reads only the bound tenant's own row through the application role's self-row RLS,
 * fresh, inside the caller's transaction. It fails closed: no bound tenant, an invisible or missing
 * row, an inactive tenant or a status without access all answer {@code false}.
 */
public interface CurrentTenantAccessPort {

  /** Call with the tenant bound (TenantContext set before the transaction began). */
  boolean currentTenantHasAccess();
}
