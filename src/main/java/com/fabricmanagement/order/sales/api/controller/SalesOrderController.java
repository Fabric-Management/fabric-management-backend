package com.fabricmanagement.order.sales.api.controller;

import com.fabricmanagement.order.sales.app.SalesOrderService;
import com.fabricmanagement.order.sales.domain.OrderStatus;
import com.fabricmanagement.order.sales.dto.CreateSalesOrderRequest;
import com.fabricmanagement.order.sales.dto.SalesOrderDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for Sales Orders.
 *
 * <p>Uses TradingPartner for customer/supplier references (Faz 1.5 pattern).
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Sales Orders", description = "Sales order management with TradingPartner integration")
public class SalesOrderController {

  private final SalesOrderService orderService;

  // ═══════════════════════════════════════════════════════════════════════════
  // CRUD
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping
  @Operation(summary = "Create a new sales order")
  public ResponseEntity<SalesOrderDto> createOrder(
      @Valid @RequestBody CreateSalesOrderRequest request) {
    SalesOrderDto order = orderService.createOrder(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(order);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get order by ID")
  public ResponseEntity<SalesOrderDto> getOrder(@PathVariable UUID id) {
    return orderService
        .findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/number/{orderNumber}")
  @Operation(summary = "Get order by order number")
  public ResponseEntity<SalesOrderDto> getOrderByNumber(@PathVariable String orderNumber) {
    return orderService
        .findByOrderNumber(orderNumber)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping
  @Operation(summary = "Get all orders (paginated)")
  public ResponseEntity<Page<SalesOrderDto>> getAllOrders(
      @PageableDefault(size = 20, sort = "orderDate") Pageable pageable) {
    return ResponseEntity.ok(orderService.findAll(pageable));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete order (soft delete)")
  public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
    orderService.deleteOrder(id);
    return ResponseEntity.noContent().build();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  @GetMapping("/partner/{partnerId}")
  @Operation(summary = "Get orders by partner ID")
  public ResponseEntity<List<SalesOrderDto>> getOrdersByPartner(@PathVariable UUID partnerId) {
    return ResponseEntity.ok(orderService.findByPartner(partnerId));
  }

  @GetMapping("/status/{status}")
  @Operation(summary = "Get orders by status")
  public ResponseEntity<List<SalesOrderDto>> getOrdersByStatus(@PathVariable OrderStatus status) {
    return ResponseEntity.ok(orderService.findByStatus(status));
  }

  @GetMapping("/open")
  @Operation(summary = "Get open orders (not delivered or cancelled)")
  public ResponseEntity<List<SalesOrderDto>> getOpenOrders() {
    return ResponseEntity.ok(orderService.findOpenOrders());
  }

  @GetMapping("/overdue")
  @Operation(summary = "Get overdue orders")
  public ResponseEntity<List<SalesOrderDto>> getOverdueOrders() {
    return ResponseEntity.ok(orderService.findOverdueOrders());
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LIFECYCLE
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping("/{id}/confirm")
  @Operation(summary = "Confirm an order")
  public ResponseEntity<SalesOrderDto> confirmOrder(@PathVariable UUID id) {
    return ResponseEntity.ok(orderService.confirmOrder(id));
  }

  @PostMapping("/{id}/process")
  @Operation(summary = "Start processing an order")
  public ResponseEntity<SalesOrderDto> startProcessing(@PathVariable UUID id) {
    return ResponseEntity.ok(orderService.startProcessing(id));
  }

  @PostMapping("/{id}/ship")
  @Operation(summary = "Ship an order")
  public ResponseEntity<SalesOrderDto> shipOrder(@PathVariable UUID id) {
    return ResponseEntity.ok(orderService.shipOrder(id));
  }

  @PostMapping("/{id}/deliver")
  @Operation(summary = "Deliver an order")
  public ResponseEntity<SalesOrderDto> deliverOrder(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "#{T(java.time.LocalDate).now()}") LocalDate deliveryDate) {
    return ResponseEntity.ok(orderService.deliverOrder(id, deliveryDate));
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "Cancel an order")
  public ResponseEntity<SalesOrderDto> cancelOrder(@PathVariable UUID id) {
    return ResponseEntity.ok(orderService.cancelOrder(id));
  }
}
