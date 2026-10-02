package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.sales.orderintake.app.OrderIntakeProductSearchService;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeProductOption;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/order-intake/products")
@RequiredArgsConstructor
@Tag(name = "Order Intake", description = "Catalogue order entry, proposals and acceptance")
public class OrderIntakeProductController {

  private final OrderIntakeProductSearchService searchService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "searchOrderIntakeProducts",
      summary = "Fibre, yarn and fabric products the customer may order")
  public ResponseEntity<ApiResponse<List<OrderIntakeProductOption>>> search(
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) ProductType productType,
      @RequestParam(required = false) String q) {
    return ResponseEntity.ok(ApiResponse.success(searchService.search(customerId, productType, q)));
  }
}
