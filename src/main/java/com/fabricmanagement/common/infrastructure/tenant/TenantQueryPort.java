package com.fabricmanagement.common.infrastructure.tenant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-module tenant sorgulama portu. Platform.tenant.infra bypass'ını önler — consumer'lar
 * TenantRepository yerine bu portu kullanır.
 */
public interface TenantQueryPort {

  /** Tüm aktif tenant'ları döndürür (scheduled job tenant iterasyonu). */
  List<TenantReference> findAllActiveTenants();

  /**
   * Every tenant that is not deleted ({@code deleted_at IS NULL}), whatever its status or active
   * flag. Used where a tenant must be provisioned even while suspended (TASK-TEMPLATE-TENANCY-1).
   */
  List<TenantReference> findAllLiveTenants();

  /**
   * The tenant that playground tenants are cloned from, resolved exactly as the playground clone
   * resolves it; empty if it does not exist or is deleted.
   */
  Optional<TenantReference> findPlaygroundSourceTenant();

  /** ID listesine göre tenant bilgisi döndürür (batch DTO enrichment). */
  List<TenantReference> findAllByIds(Collection<UUID> tenantIds);

  /** Tek tenant lookup (tekil DTO enrichment / bildirim). */
  Optional<TenantReference> findById(UUID tenantId);

  /**
   * Pre-auth tenant resolution from a registration token. Used by password setup, which is
   * anonymous (no tenant context yet) so the tenant-scoped registration-token table is invisible to
   * RLS-bound reads. Runs via the BYPASSRLS system executor in the adapter.
   */
  Optional<UUID> findTenantIdByRegistrationToken(String token);

  /**
   * Pre-auth tenant resolution from a refresh token. Refresh is anonymous (no tenant context yet)
   * so the tenant-scoped refresh-token table is invisible to RLS-bound reads. Runs via the
   * BYPASSRLS system executor in the adapter.
   */
  Optional<UUID> findTenantIdByRefreshToken(String token);
}
