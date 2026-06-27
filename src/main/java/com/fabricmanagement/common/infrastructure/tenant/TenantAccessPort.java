package com.fabricmanagement.common.infrastructure.tenant;

import java.util.UUID;

/** Cross-module access decision port for tenant lifecycle gates. */
public interface TenantAccessPort {

  /**
   * Returns whether the tenant can perform write operations.
   *
   * <p>Implementations fail open for unresolved tenants.
   */
  boolean isWritable(UUID tenantId);

  /**
   * Returns whether the tenant is currently in registered playground/demo mode.
   *
   * <p>Implementations fail closed for unresolved tenants.
   */
  boolean isDemoMode(UUID tenantId);
}
