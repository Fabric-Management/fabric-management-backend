package com.fabricmanagement.product.yarn.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.api.facade.PropertyRegistryFacade;
import com.fabricmanagement.product.core.domain.registry.PropertyRegistryException;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.reference.YarnTestMethod;
import com.fabricmanagement.product.yarn.infra.repository.YarnTestMethodRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestMethodCatalogService {

  private final YarnTestMethodRepository repository;
  private final PropertyRegistryFacade propertyRegistryFacade;

  @Transactional
  public YarnTestMethod defineTenantTestMethod(
      String code,
      String name,
      String description,
      Integer displayOrder,
      String standardRef,
      String instrument,
      String applicablePropertyKey) {
    UUID tenantId = TenantContext.requireTenantId();
    YarnTestMethod method =
        YarnTestMethod.defineTenant(
            tenantId,
            code,
            name,
            description,
            displayOrder,
            standardRef,
            instrument,
            applicablePropertyKey);
    resolveApplicableProperty(tenantId, method.getApplicablePropertyKey());
    assertCodeAvailable(tenantId, method.getCode());
    return repository.save(method);
  }

  @Transactional(readOnly = true)
  public YarnTestMethod get(UUID id) {
    return findOwned(id, TenantContext.requireTenantId());
  }

  @Transactional(readOnly = true)
  public List<YarnTestMethod> list() {
    return repository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAscCodeAsc(
        TenantContext.requireTenantId());
  }

  @Transactional(readOnly = true)
  public Page<YarnTestMethod> list(Pageable pageable) {
    return repository.findByTenantIdAndIsActiveTrue(TenantContext.requireTenantId(), pageable);
  }

  @Transactional
  public YarnTestMethod update(
      UUID id,
      String code,
      String name,
      String description,
      Integer displayOrder,
      String standardRef,
      String instrument,
      String applicablePropertyKey) {
    UUID tenantId = TenantContext.requireTenantId();
    YarnTestMethod method = findOwned(id, tenantId);
    method.update(
        code, name, description, displayOrder, standardRef, instrument, applicablePropertyKey);
    resolveApplicableProperty(tenantId, method.getApplicablePropertyKey());
    return repository.save(method);
  }

  @Transactional
  public YarnTestMethod updateMutable(
      UUID id, String name, String description, Integer displayOrder) {
    YarnTestMethod method = findOwned(id, TenantContext.requireTenantId());
    method.update(
        method.getCode(),
        name,
        description,
        displayOrder,
        method.getStandardRef(),
        method.getInstrument(),
        method.getApplicablePropertyKey());
    return repository.save(method);
  }

  @Transactional
  public YarnTestMethod deactivate(UUID id) {
    YarnTestMethod method = findOwned(id, TenantContext.requireTenantId());
    method.delete();
    return repository.save(method);
  }

  private YarnTestMethod findOwned(UUID id, UUID tenantId) {
    return repository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new YarnDomainException("Yarn test method not found: " + id));
  }

  private void assertCodeAvailable(UUID tenantId, String code) {
    if (repository.existsByTenantIdAndCode(tenantId, code)) {
      throw new YarnDomainException("Yarn test method code already exists: " + code);
    }
  }

  private void resolveApplicableProperty(UUID tenantId, String propertyKey) {
    if (propertyKey == null) {
      return;
    }
    try {
      propertyRegistryFacade.resolve(tenantId, propertyKey);
    } catch (PropertyRegistryException exception) {
      throw new YarnDomainException(
          "Yarn test method property key does not resolve: " + propertyKey, exception);
    }
  }
}
