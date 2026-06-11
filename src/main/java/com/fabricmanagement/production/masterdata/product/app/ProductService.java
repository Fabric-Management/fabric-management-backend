package com.fabricmanagement.production.masterdata.product.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.Product;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.domain.event.ProductCreatedEvent;
import com.fabricmanagement.production.masterdata.product.dto.CreateProductRequest;
import com.fabricmanagement.production.masterdata.product.dto.ProductAttributeDto;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductAttributeRepository;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product Service - Business logic for product management.
 *
 * <p>Implements ProductFacade for cross-module communication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService implements ProductFacade {

  private final ProductRepository productRepository;
  private final ProductAttributeRepository productAttributeRepository;
  private final @org.springframework.context.annotation.Lazy FiberFacade fiberFacade;
  private final DomainEventPublisher eventPublisher;

  /** Create product (internal method). */
  @Transactional
  public ProductDto createProductInternal(CreateProductRequest request) {
    log.info("Creating product: type={}", request.getProductType());

    Product product = Product.create(request.getProductType(), request.getUnit());

    Product saved = productRepository.save(product);

    eventPublisher.publish(
        new ProductCreatedEvent(saved.getTenantId(), saved.getId(), saved.getProductType()));

    log.info("✅ Product created: id={}, uid={}", saved.getId(), saved.getUid());

    ProductDto dto = ProductDto.from(saved);
    dto.setDisplayName(dto.getUid());
    return dto;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ProductDto> findById(UUID tenantId, UUID id) {
    log.debug("Finding product: tenantId={}, id={}", tenantId, id);

    return productRepository
        .findByTenantIdInAndId(tenantScope(tenantId), id)
        .map(ProductDto::from)
        .map(
            p -> {
              List<ProductDto> enriched = enrichDisplayNames(new java.util.ArrayList<>(List.of(p)));
              return enriched.get(0);
            });
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProductDto> findByTenant(UUID tenantId) {
    log.debug("Finding all products: tenantId={}", tenantId);

    List<ProductDto> products =
        productRepository.findByTenantIdInAndIsActiveTrue(tenantScope(tenantId)).stream()
            .map(ProductDto::from)
            .collect(Collectors.toList());
    return enrichDisplayNames(products);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProductDto> findByType(UUID tenantId, ProductType type) {
    log.debug("Finding products by type: tenantId={}, type={}", tenantId, type);

    List<ProductDto> products =
        productRepository
            .findByTenantIdInAndProductTypeAndIsActiveTrue(tenantScope(tenantId), type)
            .stream()
            .map(ProductDto::from)
            .collect(Collectors.toList());
    return enrichDisplayNames(products);
  }

  private List<ProductDto> enrichDisplayNames(List<ProductDto> products) {
    if (products.isEmpty()) return products;

    // Fall back to uid by default
    products.forEach(p -> p.setDisplayName(p.getUid()));

    // Collect FIBER product IDs for batch lookup
    List<UUID> fiberProductIds =
        products.stream()
            .filter(p -> ProductType.FIBER.equals(p.getProductType()))
            .map(ProductDto::getId)
            .toList();

    if (!fiberProductIds.isEmpty()) {
      Map<UUID, String> fiberNames =
          fiberFacade.findByProductIds(fiberProductIds).stream()
              .collect(
                  Collectors.toMap(
                      FiberDto::getProductId,
                      FiberDto::getFiberName,
                      (a, b) -> a // ignore duplicates
                      ));

      products.stream()
          .filter(p -> ProductType.FIBER.equals(p.getProductType()))
          .forEach(
              p -> {
                String name = fiberNames.get(p.getId());
                if (name != null) {
                  p.setDisplayName(name);
                }
              });
    }

    return products;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID tenantId, UUID id) {
    return productRepository.existsByTenantIdAndId(tenantId, id);
  }

  @Transactional
  public void deactivateProduct(UUID id) {
    UUID tenantId = TenantContext.requireTenantId();

    Product product =
        productRepository
            .findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new NotFoundException("Product not found: " + id));

    product.delete();
    productRepository.save(product);

    log.info("✅ Product deactivated: id={}", id);
  }

  @Override
  @Transactional
  public ProductDto createProduct(CreateProductRequest request) {
    return createProductInternal(request);
  }

  @Transactional(readOnly = true)
  public List<ProductAttributeDto> getAttributes(String scope) {
    if (scope != null) {
      return productAttributeRepository
          .findByIsActiveTrueAndProductScopeIn(List.of("ALL", scope))
          .stream()
          .map(ProductAttributeDto::from)
          .toList();
    }
    return productAttributeRepository.findByIsActiveTrue().stream()
        .map(ProductAttributeDto::from)
        .toList();
  }

  /**
   * Returns the tenant scope for read queries: current tenant + template tenant.
   *
   * <p>This ensures tenant users see both their own products and the platform seed products.
   */
  private List<UUID> tenantScope(UUID tenantId) {
    return List.of(
        tenantId,
        com.fabricmanagement.common.infrastructure.persistence.TenantContext.TEMPLATE_TENANT_ID);
  }
}
