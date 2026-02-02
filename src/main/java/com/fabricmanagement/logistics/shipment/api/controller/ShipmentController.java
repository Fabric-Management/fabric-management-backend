package com.fabricmanagement.logistics.shipment.api.controller;

import com.fabricmanagement.logistics.shipment.app.ShipmentService;
import com.fabricmanagement.logistics.shipment.domain.ShipmentStatus;
import com.fabricmanagement.logistics.shipment.dto.CreateShipmentRequest;
import com.fabricmanagement.logistics.shipment.dto.ShipmentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
  @Operation(summary = "Create a new shipment")
  public ResponseEntity<ShipmentDto> createShipment(
      @Valid @RequestBody CreateShipmentRequest request) {
    ShipmentDto shipment = shipmentService.createShipment(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(shipment);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get shipment by ID")
  public ResponseEntity<ShipmentDto> getShipment(@PathVariable UUID id) {
    return shipmentService
        .findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/tracking/{trackingNumber}")
  @Operation(summary = "Get shipment by tracking number")
  public ResponseEntity<ShipmentDto> getShipmentByTracking(@PathVariable String trackingNumber) {
    return shipmentService
        .findByTrackingNumber(trackingNumber)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping
  @Operation(summary = "Get all shipments (paginated)")
  public ResponseEntity<Page<ShipmentDto>> getAllShipments(
      @PageableDefault(size = 20, sort = "shipDate") Pageable pageable) {
    return ResponseEntity.ok(shipmentService.findAll(pageable));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete shipment (soft delete)")
  public ResponseEntity<Void> deleteShipment(@PathVariable UUID id) {
    shipmentService.deleteShipment(id);
    return ResponseEntity.noContent().build();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  @GetMapping("/partner/{partnerId}")
  @Operation(summary = "Get shipments by partner ID")
  public ResponseEntity<List<ShipmentDto>> getShipmentsByPartner(@PathVariable UUID partnerId) {
    return ResponseEntity.ok(shipmentService.findByPartner(partnerId));
  }

  @GetMapping("/partner/{partnerId}/in-transit")
  @Operation(summary = "Get in-transit shipments by partner")
  public ResponseEntity<List<ShipmentDto>> getInTransitByPartner(@PathVariable UUID partnerId) {
    return ResponseEntity.ok(shipmentService.findInTransitByPartner(partnerId));
  }

  @GetMapping("/status/{status}")
  @Operation(summary = "Get shipments by status")
  public ResponseEntity<List<ShipmentDto>> getShipmentsByStatus(
      @PathVariable ShipmentStatus status) {
    return ResponseEntity.ok(shipmentService.findByStatus(status));
  }

  @GetMapping("/in-transit")
  @Operation(summary = "Get all in-transit shipments")
  public ResponseEntity<List<ShipmentDto>> getInTransit() {
    return ResponseEntity.ok(shipmentService.findInTransit());
  }

  @GetMapping("/pending")
  @Operation(summary = "Get pending shipments (not yet dispatched)")
  public ResponseEntity<List<ShipmentDto>> getPendingShipments() {
    return ResponseEntity.ok(shipmentService.findPendingShipments());
  }

  @GetMapping("/late")
  @Operation(summary = "Get late shipments")
  public ResponseEntity<List<ShipmentDto>> getLateShipments() {
    return ResponseEntity.ok(shipmentService.findLateShipments());
  }

  @GetMapping("/outbound")
  @Operation(summary = "Get outbound shipments")
  public ResponseEntity<List<ShipmentDto>> getOutboundShipments() {
    return ResponseEntity.ok(shipmentService.findOutboundShipments());
  }

  @GetMapping("/inbound")
  @Operation(summary = "Get inbound shipments")
  public ResponseEntity<List<ShipmentDto>> getInboundShipments() {
    return ResponseEntity.ok(shipmentService.findInboundShipments());
  }

  @GetMapping("/order/{orderReference}")
  @Operation(summary = "Get shipments by order reference")
  public ResponseEntity<List<ShipmentDto>> getShipmentsByOrder(
      @PathVariable String orderReference) {
    return ResponseEntity.ok(shipmentService.findByOrderReference(orderReference));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LIFECYCLE
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping("/{id}/prepare")
  @Operation(summary = "Start preparing a shipment")
  public ResponseEntity<ShipmentDto> startPreparing(@PathVariable UUID id) {
    return ResponseEntity.ok(shipmentService.startPreparing(id));
  }

  @PostMapping("/{id}/ready")
  @Operation(summary = "Mark shipment as ready for pickup")
  public ResponseEntity<ShipmentDto> markReady(@PathVariable UUID id) {
    return ResponseEntity.ok(shipmentService.markReady(id));
  }

  @PostMapping("/{id}/pickup")
  @Operation(summary = "Record pickup by carrier")
  public ResponseEntity<ShipmentDto> recordPickup(
      @PathVariable UUID id,
      @RequestParam String carrierName,
      @RequestParam String trackingNumber) {
    return ResponseEntity.ok(shipmentService.recordPickup(id, carrierName, trackingNumber));
  }

  @PostMapping("/{id}/in-transit")
  @Operation(summary = "Mark shipment as in transit")
  public ResponseEntity<ShipmentDto> markInTransit(@PathVariable UUID id) {
    return ResponseEntity.ok(shipmentService.markInTransit(id));
  }

  @PostMapping("/{id}/out-for-delivery")
  @Operation(summary = "Mark shipment as out for delivery")
  public ResponseEntity<ShipmentDto> markOutForDelivery(@PathVariable UUID id) {
    return ResponseEntity.ok(shipmentService.markOutForDelivery(id));
  }

  @PostMapping("/{id}/deliver")
  @Operation(summary = "Record delivery")
  public ResponseEntity<ShipmentDto> recordDelivery(
      @PathVariable UUID id,
      @RequestParam String recipientName,
      @RequestParam(required = false) String deliveryProof) {
    return ResponseEntity.ok(shipmentService.recordDelivery(id, recipientName, deliveryProof));
  }

  @PostMapping("/{id}/delivery-failed")
  @Operation(summary = "Record delivery failure")
  public ResponseEntity<ShipmentDto> recordDeliveryFailure(
      @PathVariable UUID id, @RequestParam String reason) {
    return ResponseEntity.ok(shipmentService.recordDeliveryFailure(id, reason));
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "Cancel a shipment")
  public ResponseEntity<ShipmentDto> cancelShipment(@PathVariable UUID id) {
    return ResponseEntity.ok(shipmentService.cancelShipment(id));
  }

  @PutMapping("/{id}/tracking")
  @Operation(summary = "Update tracking info")
  public ResponseEntity<ShipmentDto> updateTracking(
      @PathVariable UUID id,
      @RequestParam String trackingNumber,
      @RequestParam(required = false) String trackingUrl) {
    return ResponseEntity.ok(shipmentService.updateTracking(id, trackingNumber, trackingUrl));
  }
}
