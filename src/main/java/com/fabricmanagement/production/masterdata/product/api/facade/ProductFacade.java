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

  /**
   * Ensure a product attribute exists for the current tenant, creating it if missing.
   *
   * <p>The lookup is tenant-scoped (TenantContext); attribute codes repeat across tenants.
   * ProductAttribute is read-only reference data with no create endpoint, so this is the sanctioned
   * write path for in-process callers (e.g. bootstrap seeders) that need a tenant-local attribute
   * axis such as COLOR.
   *
   * @param attributeCode Attribute code (e.g., COLOR)
   * @param attributeName Display name used when the attribute has to be created
   * @param attributeGroup Attribute group (e.g., VARIANT)
   * @param productScope Product scope (e.g., FIBER, YARN, FABRIC, ALL)
   * @param description Description used when the attribute has to be created
   * @param displayOrder Display order used when the attribute has to be created
   * @return Existing or newly created attribute
   */
  com.fabricmanagement.production.masterdata.product.dto.ProductAttributeDto ensureAttribute(
      String attributeCode,
      String attributeName,
      String attributeGroup,
      String productScope,
      String description,
      Integer displayOrder);
}
