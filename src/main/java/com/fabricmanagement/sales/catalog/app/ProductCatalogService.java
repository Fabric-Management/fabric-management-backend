package com.fabricmanagement.sales.catalog.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.catalog.domain.ProductCatalog;
import com.fabricmanagement.sales.catalog.dto.CreateProductCatalogRequest;
import com.fabricmanagement.sales.catalog.dto.ProductCatalogDto;
import com.fabricmanagement.sales.catalog.infra.repository.ProductCatalogRepository;
import com.fabricmanagement.sales.catalog.mapper.ProductCatalogMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCatalogService {

  private final ProductCatalogRepository repository;
  private final ProductCatalogMapper mapper;

  @Transactional(readOnly = true)
  public List<ProductCatalogDto> getActiveCatalogForModule(String moduleType) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    List<ProductCatalog> catalogs;
    if (moduleType == null || moduleType.isEmpty()) {
      catalogs = repository.findAllByTenantIdAndIsActiveTrue(tenantId);
    } else {
      catalogs = repository.findAllByTenantIdAndModuleTypeAndIsActiveTrue(tenantId, moduleType);
    }
    return catalogs.stream().map(mapper::toDto).collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public ProductCatalogDto getActiveByMaterialId(UUID materialId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return repository
        .findActiveByMaterialId(tenantId, materialId)
        .map(mapper::toDto)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Active ProductCatalog not found for material: " + materialId));
  }

  @Transactional
  public ProductCatalogDto createEntry(CreateProductCatalogRequest request) {
    ProductCatalog entry = mapper.toEntity(request);
    entry.setTenantId(TenantContext.getCurrentTenantId());
    if (entry.getSpecs() == null) entry.setSpecs("{}");
    if (entry.getPhotos() == null) entry.setPhotos("[]");
    return mapper.toDto(repository.save(entry));
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
