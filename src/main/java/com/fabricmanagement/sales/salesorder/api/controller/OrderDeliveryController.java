package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.OrderDeliveryService;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveriesView;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.SaveDeliveryRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.SetAllocationsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * An order's deliveries — consignee, place, term, requested date, ship-complete — and how its lines
 * are allocated to them (ADR-0014 D8). Every response carries the order version for the next write.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Sales Order Deliveries", description = "Deliveries and line allocation")
public class OrderDeliveryController {

  private final OrderDeliveryService deliveries;

  @GetMapping("/api/v1/sales/orders/{orderId}/deliveries")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listOrderDeliveries",
      summary = "Deliveries with their effective terms and dates, and unallocated quantities")
  public ResponseEntity<ApiResponse<DeliveriesView>> listOrderDeliveries(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(deliveries.list(orderId, actor(authentication))));
  }

  @PostMapping("/api/v1/sales/orders/{orderId}/deliveries")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(operationId = "createOrderDelivery", summary = "Add a delivery to the order")
  public ResponseEntity<ApiResponse<DeliveriesView>> createOrderDelivery(
      @PathVariable UUID orderId,
      @Valid @RequestBody SaveDeliveryRequest request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(deliveries.create(orderId, request, actor(authentication))));
  }

  @PutMapping("/api/v1/sales/orders/{orderId}/deliveries/{deliveryId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(operationId = "updateOrderDelivery", summary = "Replace what a delivery holds")
  public ResponseEntity<ApiResponse<DeliveriesView>> updateOrderDelivery(
      @PathVariable UUID orderId,
      @PathVariable UUID deliveryId,
      @Valid @RequestBody SaveDeliveryRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(
            deliveries.update(orderId, deliveryId, request, actor(authentication))));
  }

  @DeleteMapping("/api/v1/sales/orders/{orderId}/deliveries/{deliveryId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "deleteOrderDelivery",
      summary = "Remove a delivery; its allocated quantities become unallocated")
  public ResponseEntity<ApiResponse<DeliveriesView>> deleteOrderDelivery(
      @PathVariable UUID orderId,
      @PathVariable UUID deliveryId,
      @RequestParam Long expectedVersion,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(
            deliveries.delete(orderId, deliveryId, expectedVersion, actor(authentication))));
  }

  @PutMapping("/api/v1/sales/orders/{orderId}/deliveries/{deliveryId}/allocations")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "setOrderDeliveryAllocations",
      summary = "Replace how much of each line this delivery carries")
  public ResponseEntity<ApiResponse<DeliveriesView>> setOrderDeliveryAllocations(
      @PathVariable UUID orderId,
      @PathVariable UUID deliveryId,
      @Valid @RequestBody SetAllocationsRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(
            deliveries.setAllocations(orderId, deliveryId, request, actor(authentication))));
  }

  private static UUID actor(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedUserContext context) {
      return context.userId();
    }
    throw new AccessDeniedException("Authenticated user context is required.");
  }
}
