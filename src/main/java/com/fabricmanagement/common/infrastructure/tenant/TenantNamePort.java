package com.fabricmanagement.common.infrastructure.tenant;

import java.util.UUID;

/** Updates the current tenant's display name in the company profile transaction. */
public interface TenantNamePort {

  void rename(UUID tenantId, String name);
}
