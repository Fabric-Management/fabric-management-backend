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
}
