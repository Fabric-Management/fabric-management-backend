package com.fabricmanagement.procurement.purchaseorder.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderService;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.fabricmanagement.procurement.purchaseorder.dto.PurchaseOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/procurement/purchase-orders")
@RequiredArgsConstructor
@Tag(name = "Purchase Order", description = "Procurement Purchase Order API")
public class PurchaseOrderController {

  private final PurchaseOrderService purchaseOrderService;

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'procurement', 'read')")
  @Operation(summary = "Get Purchase Order by ID")
  public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getPurchaseOrder(
      @PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(purchaseOrderService.getPurchaseOrder(id)));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'procurement', 'read')")
  @Operation(summary = "List Purchase Orders")
  public ResponseEntity<ApiResponse<PagedResponse<PurchaseOrderResponse>>> listPurchaseOrders(
      @RequestParam(required = false) PurchaseOrderModuleType moduleType,
      @RequestParam(required = false) PurchaseOrderStatus status,
      Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(purchaseOrderService.listPurchaseOrders(moduleType, status, pageable)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'procurement', 'write')")
  @Operation(summary = "Create Purchase Order")
  public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createPurchaseOrder(
      @RequestBody @Valid CreatePurchaseOrderRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                purchaseOrderService.createPurchaseOrder(request), "Purchase Order created"));
  }

  /**
   * Transitions PurchaseOrder to a new status (state machine enforced). DRAFT→SENT, SENT→CONFIRMED,
   * ...→CANCELLED
   */
  @PatchMapping("/{id}/status")
  @PreAuthorize("@auth.can(authentication, 'procurement', 'write')")
  @Operation(summary = "Change Purchase Order Status")
  public ResponseEntity<ApiResponse<PurchaseOrderResponse>> changeStatus(
      @PathVariable UUID id, @RequestParam PurchaseOrderStatus status) {
    return ResponseEntity.ok(ApiResponse.success(purchaseOrderService.changeStatus(id, status)));
  }
}
