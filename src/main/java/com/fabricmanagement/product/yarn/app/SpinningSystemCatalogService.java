package com.fabricmanagement.product.yarn.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.reference.YarnSpinningSystem;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import com.fabricmanagement.product.yarn.infra.repository.YarnSpinningSystemRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpinningSystemCatalogService {

  private final YarnSpinningSystemRepository repository;

  @Transactional
  public YarnSpinningSystem defineTenantSpinningSystem(
      String code,
      String name,
      String description,
      Integer displayOrder,
      SpinningTechnologyFamily technologyFamily) {
    UUID tenantId = TenantContext.requireTenantId();
    YarnSpinningSystem system =
        YarnSpinningSystem.defineTenant(
            tenantId, code, name, description, displayOrder, technologyFamily);
    assertCodeAvailable(tenantId, system.getCode());
    return repository.save(system);
  }

  @Transactional(readOnly = true)
  public YarnSpinningSystem get(UUID id) {
    return findOwned(id, TenantContext.requireTenantId());
  }

  @Transactional(readOnly = true)
  public List<YarnSpinningSystem> list() {
    return repository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCodeAsc(
        TenantContext.requireTenantId());
  }

  @Transactional
  public YarnSpinningSystem update(
      UUID id,
      String code,
      String name,
      String description,
      Integer displayOrder,
      SpinningTechnologyFamily technologyFamily) {
    YarnSpinningSystem system = findOwned(id, TenantContext.requireTenantId());
    system.update(code, name, description, displayOrder, technologyFamily);
    return repository.save(system);
  }

  @Transactional
  public void deactivate(UUID id) {
    YarnSpinningSystem system = findOwned(id, TenantContext.requireTenantId());
    system.delete();
    repository.save(system);
  }

  private YarnSpinningSystem findOwned(UUID id, UUID tenantId) {
    return repository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new YarnDomainException("Yarn spinning system not found: " + id));
  }

  private void assertCodeAvailable(UUID tenantId, String code) {
    if (repository.existsByTenantIdAndCode(tenantId, code)) {
      throw new YarnDomainException("Yarn spinning system code already exists: " + code);
    }
  }
}
