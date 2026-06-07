package com.fabricmanagement.sales.salesproduct.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesproduct.domain.SalesProduct;
import com.fabricmanagement.sales.salesproduct.dto.CreateSalesProductRequest;
import com.fabricmanagement.sales.salesproduct.dto.SalesProductDto;
import com.fabricmanagement.sales.salesproduct.infra.repository.SalesProductRepository;
import com.fabricmanagement.sales.salesproduct.mapper.SalesProductMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesProductService {

  private final SalesProductRepository repository;
  private final SalesProductMapper mapper;

  @Transactional(readOnly = true)
  public List<SalesProductDto> getActiveCatalogForModule(String moduleType) {
    UUID tenantId = TenantContext.requireTenantId();
    List<SalesProduct> catalogs;
    if (moduleType == null || moduleType.isEmpty()) {
      catalogs = repository.findAllByTenantIdAndIsActiveTrue(tenantId);
    } else {
      catalogs = repository.findAllByTenantIdAndModuleTypeAndIsActiveTrue(tenantId, moduleType);
    }
    return catalogs.stream().map(mapper::toDto).collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public SalesProductDto getActiveByProductId(UUID productId) {
    UUID tenantId = TenantContext.requireTenantId();
    return repository
        .findActiveByProductId(tenantId, productId)
        .map(mapper::toDto)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Active SalesProduct not found for product: " + productId));
  }

  @Transactional
  public SalesProductDto createEntry(CreateSalesProductRequest request) {
    SalesProduct entry = mapper.toEntity(request);
    entry.setTenantId(TenantContext.requireTenantId());
    if (entry.getSpecs() == null) entry.setSpecs("{}");
    if (entry.getPhotos() == null) entry.setPhotos("[]");
    return mapper.toDto(repository.save(entry));
  }

  @Transactional
  public void deactivateEntry(UUID id) {
    UUID tenantId = TenantContext.requireTenantId();
    SalesProduct entry =
        repository
            .findById(id)
            .filter(e -> e.getTenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("Catalog entry not found: " + id));

    entry.markAsDeleted();
    repository.save(entry);
  }
}
