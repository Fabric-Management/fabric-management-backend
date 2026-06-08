package com.fabricmanagement.production.execution.stockunit.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitQueryService;
import com.fabricmanagement.production.execution.stockunit.app.StockUnitService;
import com.fabricmanagement.production.execution.stockunit.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for StockUnit lifecycle management.
 *
 * <p>All state-changing endpoints return the updated {@link StockUnitDto} wrapped in {@link
 * ApiResponse}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/production/stock-units")
@RequiredArgsConstructor
@Tag(name = "StockUnit", description = "Physical stock unit lifecycle management")
public class StockUnitController {

  private final StockUnitService stockUnitService;
  private final StockUnitQueryService stockUnitQueryService;

  // ── Creation ──────────────────────────────────────────────────────────────

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Create a new StockUnit for an existing Batch")
  public ResponseEntity<ApiResponse<StockUnitDto>> create(
      @Valid @RequestBody CreateStockUnitApiRequest request) {
    var unit =
        stockUnitService.create(
            request.batchId(),
            request.productType(),
            request.barcode(),
            request.serialNumber(),
            request.packageType(),
            request.initialWeight(),
            request.grossWeight(),
            request.unit(),
            request.locationId(),
            request.sourceType(),
            request.sourceId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(StockUnitDto.from(unit)));
  }

  // ── Queries ───────────────────────────────────────────────────────────────

  @GetMapping("/barcode/{barcode}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  @Operation(summary = "Lookup a StockUnit by barcode")
  public ResponseEntity<ApiResponse<StockUnitDto>> findByBarcode(@PathVariable String barcode) {
    StockUnitDto dto = stockUnitQueryService.findByBarcode(barcode);
    return ResponseEntity.ok(ApiResponse.success(dto));
  }

  @GetMapping("/batch/{batchId}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  @Operation(summary = "List active StockUnits for a batch")
  public ResponseEntity<ApiResponse<List<StockUnitDto>>> findActiveByBatch(
      @PathVariable UUID batchId) {
    List<StockUnitDto> dtos = stockUnitQueryService.findActiveByBatchId(batchId);
    return ResponseEntity.ok(ApiResponse.success(dtos));
  }

  @GetMapping("/location/{locationId}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  @Operation(summary = "List StockUnits at a warehouse location (paginated)")
  public ResponseEntity<ApiResponse<PagedResponse<StockUnitDto>>> findByLocation(
      @PathVariable UUID locationId, Pageable pageable) {
    Page<StockUnitDto> page = stockUnitQueryService.findByLocationId(locationId, pageable);
    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
  }

  // ── Consumption ───────────────────────────────────────────────────────────

  @PostMapping("/{id}/consume")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Consume weight from an AVAILABLE/PARTIAL StockUnit")
  public ResponseEntity<ApiResponse<StockUnitDto>> consume(
      @PathVariable UUID id, @Valid @RequestBody ConsumeRequest request) {
    var unit = stockUnitService.consume(id, request.amount());
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  @PostMapping("/{id}/consume-reserved")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Atomically release reservation and consume from a RESERVED StockUnit")
  public ResponseEntity<ApiResponse<StockUnitDto>> consumeReserved(
      @PathVariable UUID id, @Valid @RequestBody ConsumeReservedRequest request) {
    var unit = stockUnitService.consumeReserved(id, request.amount(), request.workOrderId());
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  @PostMapping("/{id}/reverse-consumption")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Reverse a previous consumption — adds weight back")
  public ResponseEntity<ApiResponse<StockUnitDto>> reverseConsumption(
      @PathVariable UUID id, @Valid @RequestBody ReverseConsumptionRequest request) {
    var unit = stockUnitService.reverseConsumption(id, request.amount(), request.reason());
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  // ── Transfer ──────────────────────────────────────────────────────────────

  @PostMapping("/{id}/transfer/start")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Initiate a transfer — StockUnit transitions to IN_TRANSIT")
  public ResponseEntity<ApiResponse<StockUnitDto>> startTransfer(
      @PathVariable UUID id, @Valid @RequestBody StartTransferRequest request) {
    var unit = stockUnitService.startTransfer(id, request.targetLocationId());
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  @PostMapping("/{id}/transfer/complete")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Complete a transfer — StockUnit arrives at destination")
  public ResponseEntity<ApiResponse<StockUnitDto>> completeTransfer(
      @PathVariable UUID id, @Valid @RequestBody CompleteTransferRequest request) {
    var unit = stockUnitService.completeTransfer(id, request.finalLocationId());
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  // ── Grade Change ──────────────────────────────────────────────────────────

  @PatchMapping("/{id}/grade")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Change quality grade of a StockUnit")
  public ResponseEntity<ApiResponse<StockUnitDto>> changeGrade(
      @PathVariable UUID id, @Valid @RequestBody ChangeGradeRequest request) {
    var unit =
        stockUnitService.changeGrade(
            id, request.newGradeId(), request.reason(), request.approvalId());
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  // ── Hold / Quarantine / Reservation ───────────────────────────────────────

  @PostMapping("/{id}/hold")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Put StockUnit on hold")
  public ResponseEntity<ApiResponse<StockUnitDto>> hold(
      @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
    var unit = stockUnitService.hold(id, request.reason());
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  @PostMapping("/{id}/release-hold")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Release StockUnit from hold")
  public ResponseEntity<ApiResponse<StockUnitDto>> releaseHold(
      @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
    var unit = stockUnitService.releaseHold(id, request.reason());
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  @PostMapping("/{id}/quarantine")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Quarantine a StockUnit")
  public ResponseEntity<ApiResponse<StockUnitDto>> quarantine(
      @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
    var unit = stockUnitService.quarantine(id, request.reason());
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  @PostMapping("/{id}/release-quarantine")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Release StockUnit from quarantine")
  public ResponseEntity<ApiResponse<StockUnitDto>> releaseQuarantine(
      @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
    var unit = stockUnitService.releaseQuarantine(id, request.reason());
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  @PostMapping("/{id}/reserve")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Reserve a StockUnit")
  public ResponseEntity<ApiResponse<StockUnitDto>> reserve(@PathVariable UUID id) {
    var unit = stockUnitService.reserve(id);
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  @PostMapping("/{id}/release-reserve")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Release a StockUnit reservation")
  public ResponseEntity<ApiResponse<StockUnitDto>> releaseReservation(@PathVariable UUID id) {
    var unit = stockUnitService.releaseReservation(id);
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }

  // ── Disposal ──────────────────────────────────────────────────────────────

  @PostMapping("/{id}/dispose")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  @Operation(summary = "Dispose of a StockUnit — terminal operation")
  public ResponseEntity<ApiResponse<StockUnitDto>> dispose(
      @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
    var unit = stockUnitService.dispose(id, request.reason());
    return ResponseEntity.ok(ApiResponse.success(StockUnitDto.from(unit)));
  }
}
