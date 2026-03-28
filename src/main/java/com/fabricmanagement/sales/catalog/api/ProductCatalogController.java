package com.fabricmanagement.sales.catalog.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.catalog.app.ProductCatalogService;
import com.fabricmanagement.sales.catalog.dto.CreateProductCatalogRequest;
import com.fabricmanagement.sales.catalog.dto.ProductCatalogDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/product-catalog")
@RequiredArgsConstructor
public class ProductCatalogController {

  private final ProductCatalogService catalogService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<ProductCatalogDto>>> listCatalog(
      @RequestParam(value = "moduleType", required = false) String moduleType) {
    return ResponseEntity.ok(
        ApiResponse.success(catalogService.getActiveCatalogForModule(moduleType)));
  }

  @GetMapping("/material/{materialId}")
  public ResponseEntity<ApiResponse<ProductCatalogDto>> getByMaterial(
      @PathVariable UUID materialId) {
    return ResponseEntity.ok(ApiResponse.success(catalogService.getActiveByMaterialId(materialId)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ProductCatalogDto>> createEntry(
      @Valid @RequestBody CreateProductCatalogRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(catalogService.createEntry(request)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deactivateEntry(@PathVariable UUID id) {
    catalogService.deactivateEntry(id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
