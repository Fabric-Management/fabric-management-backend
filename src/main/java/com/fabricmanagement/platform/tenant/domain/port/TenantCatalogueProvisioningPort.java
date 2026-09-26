package com.fabricmanagement.platform.tenant.domain.port;

import java.util.UUID;

/**
 * Provisions module-owned reference catalogues into a tenant while platform creates it
 * (TASK-TEMPLATE-TENANCY-1 §7). Declared by platform, implemented outside platform; platform never
 * reads or writes the catalogue tables itself.
 *
 * <p>Both methods <b>join the caller's transaction</b> and never open their own for a write, so a
 * rollback of the onboarding or of the clone leaves no catalogue rows behind. Each throws if the
 * expected transaction is not active.
 */
public interface TenantCatalogueProvisioningPort {

  /**
   * Onboarding. Must be called inside the caller's primary (JPA) transaction, with the tenant
   * context and the database session already bound to {@code targetTenantId}.
   */
  CatalogueProvisioningReport provisionFromTemplate(UUID sourceTenantId, UUID targetTenantId);

  /**
   * Playground clone. Must be called inside the caller's {@code SystemTransactionExecutor}
   * transaction. {@code targetTenantUid} is used to mint fresh row uids.
   */
  CatalogueProvisioningReport provisionPlaygroundFromSource(
      UUID sourceTenantId, UUID targetTenantId, String targetTenantUid);
}
