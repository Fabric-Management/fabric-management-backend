package com.fabricmanagement.common.infrastructure.tenant;

import java.util.Set;
import java.util.UUID;

/**
 * Cross-module decision port: which permission resources a tenant's enabled packages open.
 *
 * <p>This is the package half of API access. Role and department permissions decide what a
 * <em>user</em> may do; this decides whether the <em>tenant</em> has the package that owns the
 * resource at all. Both must allow a request, and the package half applies to administrators and
 * playground personas alike.
 *
 * <p>Implementations fail open for tenants that no package governs and for resources no package
 * claims, so an unmapped resource is never silently locked.
 */
public interface PackageAccessPort {

  /** Whether the tenant's enabled packages open the permission resource. */
  boolean isResourceOpen(UUID tenantId, String resource);

  /** Every claimed permission resource the tenant's enabled packages do not currently open. */
  Set<String> closedResources(UUID tenantId);
}
