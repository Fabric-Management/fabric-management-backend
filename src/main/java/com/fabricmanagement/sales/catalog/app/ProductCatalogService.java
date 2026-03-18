package com.fabricmanagement.sales.catalog.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.catalog.domain.ProductCatalog;
import com.fabricmanagement.sales.catalog.infra.repository.ProductCatalogRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCatalogService {

  private final ProductCatalogRepository repository;

  @Transactional(readOnly = true)
  public List<ProductCatalog> getActiveCatalogForModule(String moduleType) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (moduleType == null || moduleType.isEmpty()) {
      return repository.findAllByTenantIdAndIsActiveTrue(tenantId);
    }
    return repository.findAllByTenantIdAndModuleTypeAndIsActiveTrue(tenantId, moduleType);
  }

  @Transactional(readOnly = true)
  public ProductCatalog getActiveByMaterialId(UUID materialId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return repository
        .findActiveByMaterialId(tenantId, materialId)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Active ProductCatalog not found for material: " + materialId));
  }

  @Transactional
  public ProductCatalog createEntry(ProductCatalog entry) {
    entry.setTenantId(TenantContext.getCurrentTenantId());
    return repository.save(entry);
  }

  @Transactional
  public void deactivateEntry(UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    ProductCatalog entry =
        repository
            .findById(id)
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("Catalog entry not found: " + id));

    entry.markAsDeleted();
    repository.save(entry);
  }
}
