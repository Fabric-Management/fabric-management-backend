package com.fabricmanagement.platform.tenant.app.adapter;

import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantQueryAdapter implements TenantQueryPort {

  private final TenantRepository tenantRepository;

  @Override
  public List<TenantReference> findAllActiveTenants() {
    return tenantRepository.findAllActive().stream()
        .map(t -> new TenantReference(t.getId(), t.getUid(), t.getName()))
        .toList();
  }

  @Override
  public List<TenantReference> findAllByIds(Collection<UUID> tenantIds) {
    return tenantRepository.findAllById(tenantIds).stream()
        .map(t -> new TenantReference(t.getId(), t.getUid(), t.getName()))
        .toList();
  }

  @Override
  public Optional<TenantReference> findById(UUID tenantId) {
    return tenantRepository
        .findById(tenantId)
        .map(t -> new TenantReference(t.getId(), t.getUid(), t.getName()));
  }
}
