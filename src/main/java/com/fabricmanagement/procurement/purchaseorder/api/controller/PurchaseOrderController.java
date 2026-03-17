package com.fabricmanagement.procurement.purchaseorder.api.controller;

import com.fabricmanagement.procurement.purchaseorder.app.PurchaseOrderService;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.fabricmanagement.procurement.purchaseorder.dto.PurchaseOrderResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/procurement/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

  private final PurchaseOrderService purchaseOrderService;

  @GetMapping("/{id}")
  public PurchaseOrderResponse getPurchaseOrder(@PathVariable UUID id) {
    return purchaseOrderService.getPurchaseOrder(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PurchaseOrderResponse createPurchaseOrder(
      @RequestBody @Valid CreatePurchaseOrderRequest request) {
    return purchaseOrderService.createPurchaseOrder(request);
  }

  /**
   * Transitions PurchaseOrder to a new status (state machine enforced). DRAFT→SENT, SENT→CONFIRMED,
   * ...→CANCELLED
   */
  @PatchMapping("/{id}/status")
  public PurchaseOrderResponse changeStatus(
      @PathVariable UUID id, @RequestParam PurchaseOrderStatus status) {
    return purchaseOrderService.changeStatus(id, status);
  }
}
