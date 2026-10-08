package com.fabricmanagement.platform.tenant.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.CurrentTenantAccessPort;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bound tenant's own access, read through RLS (self-row policy of {@code common_tenant}): the
 * same rule as {@code Tenant.hasAccess()} — an active tenant in TRIAL, ACTIVE or the read-only
 * EXPIRED status. Not cached: a suspension or deactivation counts from the next read.
 */
@Component
@RequiredArgsConstructor
public class CurrentTenantAccessAdapter implements CurrentTenantAccessPort {

  private final TenantRepository tenants;

  @Override
  @Transactional(readOnly = true)
  public boolean currentTenantHasAccess() {
    UUID tenantId = TenantContext.getCurrentTenantIdOrNull();
    if (tenantId == null || TenantContext.SYSTEM_TENANT_ID.equals(tenantId)) {
      return false;
    }
    return tenants
        .findAccessViewById(tenantId)
        .map(
            tenant ->
                Boolean.TRUE.equals(tenant.getIsActive())
                    && tenant.getStatus() != null
                    && tenant.getStatus().hasAccess())
        .orElse(false);
  }
}
