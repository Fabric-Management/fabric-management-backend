package com.fabricmanagement.production.masterdata.product.api.facade;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.CreateProductRequest;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Product Facade - Internal API for cross-module communication.
 *
 * <p>Other modules should interact with Product through this facade.
 *
 * <p>This is IN-PROCESS communication (no HTTP overhead).
 */
public interface ProductFacade {

  /**
   * Find product by ID.
   *
   * @param tenantId Tenant ID
   * @param id Product ID
   * @return Product DTO if found
   */
  Optional<ProductDto> findById(UUID tenantId, UUID id);

  /**
   * Find all products for tenant.
   *
   * @param tenantId Tenant ID
   * @return List of products
   */
  List<ProductDto> findByTenant(UUID tenantId);

  /**
   * Find products by type.
   *
   * @param tenantId Tenant ID
   * @param type Product type
   * @return List of products
   */
  List<ProductDto> findByType(UUID tenantId, ProductType type);

  /**
   * Check if product exists.
   *
   * @param tenantId Tenant ID
   * @param id Product ID
   * @return true if exists
   */
  boolean exists(UUID tenantId, UUID id);

  /**
   * Create a new product.
   *
   * @param request Create product request
   * @return Created product DTO
   */
  ProductDto createProduct(CreateProductRequest request);

  /**
   * Get product attributes by scope.
   *
   * @param scope Product scope (e.g., FIBER, YARN, FABRIC, ALL)
   * @return List of product attributes
   */
  List<com.fabricmanagement.production.masterdata.product.dto.ProductAttributeDto> getAttributes(
      String scope);
}
