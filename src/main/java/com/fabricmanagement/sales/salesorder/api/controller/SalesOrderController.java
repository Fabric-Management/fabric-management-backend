package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for Sales Orders.
 *
 * <p>Uses TradingPartner for customer/supplier references (Faz 1.5 pattern).
 */
@RestController
@RequestMapping("/api/v1/sales/orders")
@RequiredArgsConstructor
@Tag(name = "Sales Orders", description = "Sales order management with TradingPartner integration")
public class SalesOrderController {

  private final SalesOrderService orderService;

  // ═══════════════════════════════════════════════════════════════════════════
  // CRUD
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Create a new sales order")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "201",
      description = "Order created successfully")
  public ResponseEntity<ApiResponse<SalesOrderDto>> createOrder(
      @Valid @RequestBody CreateSalesOrderRequest request) {
    SalesOrderDto order = orderService.createOrder(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(order));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      summary = "Update a draft sales order",
      description =
          "Only DRAFT orders can be updated. "
              + "Requires version field for optimistic locking. "
              + "Lines use full-replace strategy: lines with id are updated, "
              + "lines without id are created, missing lines are soft-deleted.")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "200",
      description = "Order updated successfully")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(
      responseCode = "409",
      description = "Optimistic locking conflict or order not in DRAFT status")
  public ResponseEntity<ApiResponse<SalesOrderDto>> updateOrder(
      @PathVariable UUID id, @Valid @RequestBody UpdateSalesOrderRequest request) {
    SalesOrderDto order = orderService.updateOrder(id, request);
    return ResponseEntity.ok(ApiResponse.success(order, "Sales order updated"));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get order by ID")
  public ResponseEntity<ApiResponse<SalesOrderDto>> getOrder(@PathVariable UUID id) {
    return ResponseEntity.ok(
        ApiResponse.success(
            orderService
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sales order not found: " + id))));
  }

  @GetMapping("/number/{orderNumber}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get order by order number")
  public ResponseEntity<ApiResponse<SalesOrderDto>> getOrderByNumber(
      @PathVariable String orderNumber) {
    return ResponseEntity.ok(
        ApiResponse.success(
            orderService
                .findByOrderNumber(orderNumber)
                .orElseThrow(
                    () -> new EntityNotFoundException("Sales order not found: " + orderNumber))));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get all orders (paginated)")
  public ResponseEntity<ApiResponse<PagedResponse<SalesOrderDto>>> getAllOrders(
      @PageableDefault(size = 20, sort = "orderDate") Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(orderService.findAll(pageable))));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'delete')")
  @Operation(summary = "Delete order (soft delete)")
  public ResponseEntity<ApiResponse<Void>> deleteOrder(@PathVariable UUID id) {
    orderService.deleteOrder(id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  @GetMapping("/partner/{partnerId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get orders by partner ID")
  public ResponseEntity<ApiResponse<List<SalesOrderDto>>> getOrdersByPartner(
      @PathVariable UUID partnerId) {
    return ResponseEntity.ok(ApiResponse.success(orderService.findByPartner(partnerId)));
  }

  @GetMapping("/status/{status}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get orders by status")
  public ResponseEntity<ApiResponse<List<SalesOrderDto>>> getOrdersByStatus(
      @PathVariable OrderStatus status) {
    return ResponseEntity.ok(ApiResponse.success(orderService.findByStatus(status)));
  }

  @GetMapping("/open")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get open orders (not delivered or cancelled)")
  public ResponseEntity<ApiResponse<List<SalesOrderDto>>> getOpenOrders() {
    return ResponseEntity.ok(ApiResponse.success(orderService.findOpenOrders()));
  }

  @GetMapping("/overdue")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get overdue orders")
  public ResponseEntity<ApiResponse<List<SalesOrderDto>>> getOverdueOrders() {
    return ResponseEntity.ok(ApiResponse.success(orderService.findOverdueOrders()));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LIFECYCLE
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping("/{id}/confirm")
  @PreAuthorize("@auth.can(authentication, 'sales', 'confirm')")
  @Operation(summary = "Confirm an order")
  public ResponseEntity<ApiResponse<SalesOrderDto>> confirmOrder(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(orderService.confirmOrder(id)));
  }

  @PostMapping("/{id}/process")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Start processing an order")
  public ResponseEntity<ApiResponse<SalesOrderDto>> startProcessing(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(orderService.startProcessing(id)));
  }

  @PostMapping("/{id}/ship")
  @PreAuthorize("@auth.can(authentication, 'sales', 'ship')")
  @Operation(summary = "Ship an order")
  public ResponseEntity<ApiResponse<SalesOrderDto>> shipOrder(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(orderService.shipOrder(id)));
  }

  @PostMapping("/{id}/deliver")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Deliver an order")
  public ResponseEntity<ApiResponse<SalesOrderDto>> deliverOrder(
      @PathVariable UUID id, @RequestParam(required = false) LocalDate deliveryDate) {
    LocalDate effectiveDate = deliveryDate != null ? deliveryDate : LocalDate.now();
    return ResponseEntity.ok(ApiResponse.success(orderService.deliverOrder(id, effectiveDate)));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("@auth.can(authentication, 'sales', 'cancel')")
  @Operation(summary = "Cancel an order")
  public ResponseEntity<ApiResponse<SalesOrderDto>> cancelOrder(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(orderService.cancelOrder(id)));
  }
}
