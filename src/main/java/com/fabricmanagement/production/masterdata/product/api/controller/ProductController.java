package com.fabricmanagement.production.masterdata.product.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.masterdata.product.app.ProductService;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.CreateProductRequest;
import com.fabricmanagement.production.masterdata.product.dto.ProductAttributeDto;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Product Controller - REST endpoints for product management.
 *
 * <p>Security uses department-aware checks via {@code PermissionEvaluator}. WRITE = create /
 * deactivate (ADMIN, or MANAGER in R&D / Prod. Planning / Fiber dept). READ = any authenticated
 * user in a production-related department.
 */
@RestController
@RequestMapping("/api/v1/production/products")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product", description = "Product Management API")
public class ProductController {

  private final ProductService productService;

  @Operation(summary = "Create Product")
  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public ResponseEntity<ApiResponse<ProductDto>> createProduct(
      @Valid @RequestBody CreateProductRequest request) {

    log.info("Creating product: type={}", request.getProductType());

    ProductDto created = productService.createProduct(request);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(created, "Product created successfully"));
  }

  @Operation(summary = "Get Product by ID")
  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<ProductDto>> getProduct(
      @Parameter(description = "Product ID") @PathVariable UUID id) {
    UUID tenantId = TenantContext.requireTenantId();

    ProductDto product =
        productService
            .findById(tenantId, id)
            .orElseThrow(() -> new NotFoundException("Product not found: " + id));

    return ResponseEntity.ok(ApiResponse.success(product));
  }

  @Operation(summary = "Get All Products")
  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<List<ProductDto>>> getAllProducts() {
    UUID tenantId = TenantContext.requireTenantId();

    List<ProductDto> products = productService.findByTenant(tenantId);

    return ResponseEntity.ok(ApiResponse.success(products));
  }

  @Operation(summary = "Get Products by Type")
  @GetMapping("/type/{type}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<List<ProductDto>>> getProductsByType(
      @Parameter(description = "Product Type") @PathVariable ProductType type) {
    UUID tenantId = TenantContext.requireTenantId();

    List<ProductDto> products = productService.findByType(tenantId, type);

    return ResponseEntity.ok(ApiResponse.success(products));
  }

  @Operation(summary = "Get Product Attributes")
  @GetMapping("/attributes")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public ResponseEntity<ApiResponse<List<ProductAttributeDto>>> getAttributes(
      @Parameter(description = "Product Scope (e.g., FIBER, YARN, FABRIC)")
          @RequestParam(required = false)
          String scope) {
    List<ProductAttributeDto> attributes = productService.getAttributes(scope);
    return ResponseEntity.ok(ApiResponse.success(attributes));
  }

  @Operation(summary = "Deactivate Product")
  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public ResponseEntity<ApiResponse<Void>> deactivateProduct(
      @Parameter(description = "Product ID") @PathVariable UUID id) {
    log.info("Deactivating product: id={}", id);

    productService.deactivateProduct(id);

    return ResponseEntity.ok(ApiResponse.success(null, "Product deactivated successfully"));
  }
}
