package com.fabricmanagement.logistics.shipment.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.logistics.shipment.app.ShipmentService;
import com.fabricmanagement.logistics.shipment.domain.ShipmentStatus;
import com.fabricmanagement.logistics.shipment.dto.CreateShipmentRequest;
import com.fabricmanagement.logistics.shipment.dto.ShipmentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for Shipments.
 *
 * <p>Uses TradingPartner for customer/supplier references (Faz 1.5 pattern). Supports both inbound
 * and outbound shipments.
 */
@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments", description = "Shipment management with TradingPartner integration")
public class ShipmentController {

  private final ShipmentService shipmentService;

  // ═══════════════════════════════════════════════════════════════════════════
  // CRUD
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'logistics', 'write')")
  @Operation(summary = "Create a new shipment")
  public ResponseEntity<ApiResponse<ShipmentDto>> createShipment(
      @Valid @RequestBody CreateShipmentRequest request) {
    ShipmentDto shipment = shipmentService.createShipment(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(shipment));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get shipment by ID")
  public ResponseEntity<ApiResponse<ShipmentDto>> getShipment(@PathVariable UUID id) {
    return ResponseEntity.ok(
        ApiResponse.success(
            shipmentService
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Shipment not found: " + id))));
  }

  @GetMapping("/tracking/{trackingNumber}")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get shipment by tracking number")
  public ResponseEntity<ApiResponse<ShipmentDto>> getShipmentByTracking(
      @PathVariable String trackingNumber) {
    return ResponseEntity.ok(
        ApiResponse.success(
            shipmentService
                .findByTrackingNumber(trackingNumber)
                .orElseThrow(
                    () -> new EntityNotFoundException("Shipment not found: " + trackingNumber))));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get all shipments (paginated)")
  public ResponseEntity<ApiResponse<PagedResponse<ShipmentDto>>> getAllShipments(
      @PageableDefault(size = 20, sort = "shipDate") Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(shipmentService.findAll(pageable))));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'delete')")
  @Operation(summary = "Delete shipment (soft delete)")
  public ResponseEntity<ApiResponse<Void>> deleteShipment(@PathVariable UUID id) {
    shipmentService.deleteShipment(id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  @GetMapping("/partner/{partnerId}")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get shipments by partner ID")
  public ResponseEntity<ApiResponse<List<ShipmentDto>>> getShipmentsByPartner(
      @PathVariable UUID partnerId) {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.findByPartner(partnerId)));
  }

  @GetMapping("/partner/{partnerId}/in-transit")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get in-transit shipments by partner")
  public ResponseEntity<ApiResponse<List<ShipmentDto>>> getInTransitByPartner(
      @PathVariable UUID partnerId) {
    return ResponseEntity.ok(
        ApiResponse.success(shipmentService.findInTransitByPartner(partnerId)));
  }

  @GetMapping("/status/{status}")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get shipments by status")
  public ResponseEntity<ApiResponse<List<ShipmentDto>>> getShipmentsByStatus(
      @PathVariable ShipmentStatus status) {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.findByStatus(status)));
  }

  @GetMapping("/in-transit")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get all in-transit shipments")
  public ResponseEntity<ApiResponse<List<ShipmentDto>>> getInTransit() {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.findInTransit()));
  }

  @GetMapping("/pending")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get pending shipments (not yet dispatched)")
  public ResponseEntity<ApiResponse<List<ShipmentDto>>> getPendingShipments() {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.findPendingShipments()));
  }

  @GetMapping("/late")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get late shipments")
  public ResponseEntity<ApiResponse<List<ShipmentDto>>> getLateShipments() {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.findLateShipments()));
  }

  @GetMapping("/outbound")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get outbound shipments")
  public ResponseEntity<ApiResponse<List<ShipmentDto>>> getOutboundShipments() {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.findOutboundShipments()));
  }

  @GetMapping("/inbound")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get inbound shipments")
  public ResponseEntity<ApiResponse<List<ShipmentDto>>> getInboundShipments() {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.findInboundShipments()));
  }

  @GetMapping("/order/{orderReference}")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(summary = "Get shipments by order reference")
  public ResponseEntity<ApiResponse<List<ShipmentDto>>> getShipmentsByOrder(
      @PathVariable String orderReference) {
    return ResponseEntity.ok(
        ApiResponse.success(shipmentService.findByOrderReference(orderReference)));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LIFECYCLE
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping("/{id}/prepare")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'prepare')")
  @Operation(summary = "Start preparing a shipment")
  public ResponseEntity<ApiResponse<ShipmentDto>> startPreparing(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.startPreparing(id)));
  }

  @PostMapping("/{id}/ready")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'prepare')")
  @Operation(summary = "Mark shipment as ready for pickup")
  public ResponseEntity<ApiResponse<ShipmentDto>> markReady(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.markReady(id)));
  }

  @PostMapping("/{id}/pickup")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'ship')")
  @Operation(summary = "Record pickup by carrier")
  public ResponseEntity<ApiResponse<ShipmentDto>> recordPickup(
      @PathVariable UUID id,
      @RequestParam String carrierName,
      @RequestParam String trackingNumber) {
    return ResponseEntity.ok(
        ApiResponse.success(shipmentService.recordPickup(id, carrierName, trackingNumber)));
  }

  @PostMapping("/{id}/in-transit")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'ship')")
  @Operation(summary = "Mark shipment as in transit")
  public ResponseEntity<ApiResponse<ShipmentDto>> markInTransit(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.markInTransit(id)));
  }

  @PostMapping("/{id}/out-for-delivery")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'ship')")
  @Operation(summary = "Mark shipment as out for delivery")
  public ResponseEntity<ApiResponse<ShipmentDto>> markOutForDelivery(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.markOutForDelivery(id)));
  }

  @PostMapping("/{id}/deliver")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'deliver')")
  @Operation(summary = "Record delivery")
  public ResponseEntity<ApiResponse<ShipmentDto>> recordDelivery(
      @PathVariable UUID id,
      @RequestParam String recipientName,
      @RequestParam(required = false) String deliveryProof) {
    return ResponseEntity.ok(
        ApiResponse.success(shipmentService.recordDelivery(id, recipientName, deliveryProof)));
  }

  @PostMapping("/{id}/delivery-failed")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'deliver')")
  @Operation(summary = "Record delivery failure")
  public ResponseEntity<ApiResponse<ShipmentDto>> recordDeliveryFailure(
      @PathVariable UUID id, @RequestParam String reason) {
    return ResponseEntity.ok(
        ApiResponse.success(shipmentService.recordDeliveryFailure(id, reason)));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'cancel')")
  @Operation(summary = "Cancel a shipment")
  public ResponseEntity<ApiResponse<ShipmentDto>> cancelShipment(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(shipmentService.cancelShipment(id)));
  }

  @PutMapping("/{id}/tracking")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'write')")
  @Operation(summary = "Update tracking info")
  public ResponseEntity<ApiResponse<ShipmentDto>> updateTracking(
      @PathVariable UUID id,
      @RequestParam String trackingNumber,
      @RequestParam(required = false) String trackingUrl) {
    return ResponseEntity.ok(
        ApiResponse.success(shipmentService.updateTracking(id, trackingNumber, trackingUrl)));
  }
}
