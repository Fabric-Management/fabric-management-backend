package com.fabricmanagement.production.execution.batch.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.execution.batch.app.BatchService;
import com.fabricmanagement.production.execution.batch.dto.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for managing batches with named-reservation logic.
 *
 * <p>Security uses department-aware checks via {@code ProductionAccessService}. WRITE = create /
 * reserve / release / consume / adjust (ADMIN, or MANAGER/SUPERVISOR in production depts). READ =
 * any authenticated user in a production-related department.
 */
@RestController
@RequestMapping("/api/production/batches")
@RequiredArgsConstructor
@Slf4j
public class BatchController {

  private final BatchService batchService;

  // ── Batch CRUD ─────────────────────────────────────────────────────────────

  @PostMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> createBatch(
      @Valid @RequestBody CreateBatchRequest request) {
    BatchDto batch = batchService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(batch));
  }

  @GetMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<BatchDto>>> getAllBatches() {
    List<BatchDto> batches = batchService.getAll();
    return ResponseEntity.ok(ApiResponse.success(batches));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<BatchDto>> getBatch(@PathVariable UUID id) {
    return batchService
        .getById(id)
        .map(batch -> ResponseEntity.ok(ApiResponse.success(batch)))
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/material/{materialId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<BatchDto>>> getBatchesByMaterialId(
      @PathVariable UUID materialId) {
    List<BatchDto> batches = batchService.getByMaterialId(materialId);
    return ResponseEntity.ok(ApiResponse.success(batches));
  }

  // ── Named Reservation ──────────────────────────────────────────────────────

  @PostMapping("/{id}/reserve")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchReservationDto>> reserve(
      @PathVariable UUID id, @Valid @RequestBody ReserveRequest request) {
    BatchReservationDto reservation = batchService.reserve(id, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(reservation));
  }

  @DeleteMapping("/{id}/reservations/{reservationId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> releaseReservation(
      @PathVariable UUID id, @PathVariable UUID reservationId) {
    BatchDto batch = batchService.releaseReservation(id, reservationId);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  @PostMapping("/{id}/reservations/{reservationId}/complete")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> completeReservation(
      @PathVariable UUID id, @PathVariable UUID reservationId) {
    BatchDto batch = batchService.completeReservation(id, reservationId);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  @GetMapping("/{id}/reservations")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<BatchReservationDto>>> getReservations(
      @PathVariable UUID id) {
    List<BatchReservationDto> reservations = batchService.getReservations(id);
    return ResponseEntity.ok(ApiResponse.success(reservations));
  }

  // ── Consume ────────────────────────────────────────────────────────────────

  @PostMapping("/{id}/consume")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> consume(
      @PathVariable UUID id, @Valid @RequestBody ConsumeRequest request) {
    BatchDto batch = batchService.consume(id, request);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  // ── Waste ──────────────────────────────────────────────────────────────────

  @PostMapping("/{id}/waste")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> recordWaste(
      @PathVariable UUID id, @Valid @RequestBody QuantityRequest request) {
    BatchDto batch = batchService.recordWaste(id, request.getQuantity());
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  // ── Inventory Adjustment ───────────────────────────────────────────────────

  @PostMapping("/{id}/adjust")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> adjust(
      @PathVariable UUID id, @Valid @RequestBody AdjustmentRequest request) {
    BatchDto batch = batchService.adjust(id, request);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  // ── Start Production (WIP) ─────────────────────────────────────────────────

  @PostMapping("/{id}/start-production")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> startProduction(
      @PathVariable UUID id, @Valid @RequestBody StartProductionRequest request) {
    BatchDto batch = batchService.startProduction(id, request);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  // ── Split & Transfer ───────────────────────────────────────────────────────

  @PostMapping("/{id}/split")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> splitBatch(
      @PathVariable UUID id, @Valid @RequestBody SplitBatchRequest request) {
    BatchDto batch = batchService.splitBatch(id, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(batch));
  }

  @PostMapping("/{id}/transfer")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<BatchDto>> transferBatch(
      @PathVariable UUID id, @Valid @RequestBody TransferBatchRequest request) {
    BatchDto batch = batchService.transferBatch(id, request);
    return ResponseEntity.ok(ApiResponse.success(batch));
  }
}
