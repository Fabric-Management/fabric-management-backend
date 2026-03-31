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

  /** ID listesine göre tenant bilgisi döndürür (batch DTO enrichment). */
  List<TenantReference> findAllByIds(Collection<UUID> tenantIds);

  /** Tek tenant lookup (tekil DTO enrichment / bildirim). */
  Optional<TenantReference> findById(UUID tenantId);
}
