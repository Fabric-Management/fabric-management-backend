package com.fabricmanagement.sales.salesproduct.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesproduct.app.SalesProductService;
import com.fabricmanagement.sales.salesproduct.dto.CreateSalesProductRequest;
import com.fabricmanagement.sales.salesproduct.dto.SalesProductDto;
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
@RequestMapping("/api/v1/sales/products")
@RequiredArgsConstructor
public class SalesProductController {

  private final SalesProductService salesProductService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<SalesProductDto>>> listCatalog(
      @RequestParam(value = "moduleType", required = false) String moduleType) {
    return ResponseEntity.ok(
        ApiResponse.success(salesProductService.getActiveCatalogForModule(moduleType)));
  }

  @GetMapping("/product/{productId}")
  public ResponseEntity<ApiResponse<SalesProductDto>> getByProduct(@PathVariable UUID productId) {
    return ResponseEntity.ok(
        ApiResponse.success(salesProductService.getActiveByProductId(productId)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<SalesProductDto>> createEntry(
      @Valid @RequestBody CreateSalesProductRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(salesProductService.createEntry(request)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deactivateEntry(@PathVariable UUID id) {
    salesProductService.deactivateEntry(id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
