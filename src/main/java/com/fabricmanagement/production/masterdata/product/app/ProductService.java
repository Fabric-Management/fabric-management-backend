package com.fabricmanagement.production.masterdata.product.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
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
  private final FiberRepository fiberRepository;
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

    return ProductDto.from(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ProductDto> findById(UUID tenantId, UUID id) {
    log.debug("Finding product: tenantId={}, id={}", tenantId, id);

    return productRepository
        .findByTenantIdAndId(tenantId, id)
        .map(ProductDto::from)
        .map(p -> enrichDisplayNames(List.of(p)).get(0));
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProductDto> findByTenant(UUID tenantId) {
    log.debug("Finding all products: tenantId={}", tenantId);

    List<ProductDto> products =
        productRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
            .map(ProductDto::from)
            .toList();
    return enrichDisplayNames(products);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProductDto> findByType(UUID tenantId, ProductType type) {
    log.debug("Finding products by type: tenantId={}, type={}", tenantId, type);

    List<ProductDto> products =
        productRepository.findByTenantIdAndProductTypeAndIsActiveTrue(tenantId, type).stream()
            .map(ProductDto::from)
            .toList();
    return enrichDisplayNames(products);
  }

  private List<ProductDto> enrichDisplayNames(List<ProductDto> products) {
    if (products.isEmpty()) return products;

    // Fall back to uid by default
    products.forEach(p -> p.setDisplayName(p.getUid()));

    // Collect FIBER products
    List<UUID> fiberProductIds =
        products.stream()
            .filter(p -> ProductType.FIBER.equals(p.getProductType()))
            .map(ProductDto::getId)
            .toList();

    if (!fiberProductIds.isEmpty()) {
      Map<UUID, String> fiberNames =
          fiberRepository.findByProductIdIn(fiberProductIds).stream()
              .collect(
                  Collectors.toMap(
                      f -> f.getProduct().getId(),
                      Fiber::getFiberName,
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
}
