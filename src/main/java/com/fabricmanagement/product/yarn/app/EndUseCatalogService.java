package com.fabricmanagement.product.yarn.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.reference.YarnEndUse;
import com.fabricmanagement.product.yarn.infra.repository.YarnEndUseRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EndUseCatalogService {

  private final YarnEndUseRepository repository;

  @Transactional
  public YarnEndUse defineTenantEndUse(
      String code, String name, String description, Integer displayOrder) {
    UUID tenantId = TenantContext.requireTenantId();
    YarnEndUse endUse = YarnEndUse.defineTenant(tenantId, code, name, description, displayOrder);
    assertCodeAvailable(tenantId, endUse.getCode());
    return repository.save(endUse);
  }

  @Transactional(readOnly = true)
  public YarnEndUse get(UUID id) {
    return findOwned(id, TenantContext.requireTenantId());
  }

  @Transactional(readOnly = true)
  public List<YarnEndUse> list() {
    return repository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCodeAsc(
        TenantContext.requireTenantId());
  }

  @Transactional(readOnly = true)
  public Page<YarnEndUse> list(Pageable pageable) {
    return repository.findByTenantIdAndIsActiveTrue(TenantContext.requireTenantId(), pageable);
  }

  @Transactional
  public YarnEndUse update(
      UUID id, String code, String name, String description, Integer displayOrder) {
    YarnEndUse endUse = findOwned(id, TenantContext.requireTenantId());
    endUse.update(code, name, description, displayOrder);
    return repository.save(endUse);
  }

  @Transactional
  public YarnEndUse updateMutable(UUID id, String name, String description, Integer displayOrder) {
    YarnEndUse endUse = findOwned(id, TenantContext.requireTenantId());
    endUse.update(endUse.getCode(), name, description, displayOrder);
    return repository.save(endUse);
  }

  @Transactional
  public YarnEndUse deactivate(UUID id) {
    YarnEndUse endUse = findOwned(id, TenantContext.requireTenantId());
    endUse.delete();
    return repository.save(endUse);
  }

  private YarnEndUse findOwned(UUID id, UUID tenantId) {
    return repository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new YarnDomainException("Yarn end-use not found: " + id));
  }

  private void assertCodeAvailable(UUID tenantId, String code) {
    if (repository.existsByTenantIdAndCode(tenantId, code)) {
      throw new YarnDomainException("Yarn end-use code already exists: " + code);
    }
  }
}
