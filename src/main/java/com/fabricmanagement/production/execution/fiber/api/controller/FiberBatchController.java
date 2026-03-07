package com.fabricmanagement.production.execution.fiber.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.execution.fiber.app.FiberBatchService;
import com.fabricmanagement.production.execution.fiber.dto.CreateFiberBatchRequest;
import com.fabricmanagement.production.execution.fiber.dto.FiberBatchDto;
import com.fabricmanagement.production.execution.fiber.dto.QuantityRequest;
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
 * REST API for managing fiber batches with production-ready reservation logic.
 *
 * <p>Security uses department-aware checks via {@code ProductionAccessService}. WRITE = create /
 * reserve / release / consume (ADMIN, or MANAGER/SUPERVISOR in production depts). READ = any
 * authenticated user in a production-related department.
 */
@RestController
@RequestMapping("/api/production/batches/fiber")
@RequiredArgsConstructor
@Slf4j
public class FiberBatchController {

  private final FiberBatchService fiberBatchService;

  @PostMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberBatchDto>> createBatch(
      @Valid @RequestBody CreateFiberBatchRequest request) {
    FiberBatchDto batch = fiberBatchService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(batch));
  }

  @GetMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberBatchDto>>> getAllBatches() {
    List<FiberBatchDto> batches = fiberBatchService.getAll();
    return ResponseEntity.ok(ApiResponse.success(batches));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'READ')")
  public ResponseEntity<ApiResponse<FiberBatchDto>> getBatch(@PathVariable UUID id) {
    return fiberBatchService
        .getById(id)
        .map(batch -> ResponseEntity.ok(ApiResponse.success(batch)))
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/fiber/{fiberId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberBatchDto>>> getBatchesByFiberId(
      @PathVariable UUID fiberId) {
    List<FiberBatchDto> batches = fiberBatchService.getByFiberId(fiberId);
    return ResponseEntity.ok(ApiResponse.success(batches));
  }

  @PostMapping("/{id}/reserve")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberBatchDto>> reserve(
      @PathVariable UUID id, @Valid @RequestBody QuantityRequest request) {
    FiberBatchDto batch = fiberBatchService.reserve(id, request.getQuantity());
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  @PostMapping("/{id}/release")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberBatchDto>> release(
      @PathVariable UUID id, @Valid @RequestBody QuantityRequest request) {
    FiberBatchDto batch = fiberBatchService.release(id, request.getQuantity());
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  @PostMapping("/{id}/consume")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberBatchDto>> consume(
      @PathVariable UUID id, @Valid @RequestBody QuantityRequest request) {
    FiberBatchDto batch = fiberBatchService.consume(id, request.getQuantity());
    return ResponseEntity.ok(ApiResponse.success(batch));
  }

  @PostMapping("/{id}/waste")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER_BATCH', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberBatchDto>> recordWaste(
      @PathVariable UUID id, @Valid @RequestBody QuantityRequest request) {
    FiberBatchDto batch = fiberBatchService.recordWaste(id, request.getQuantity());
    return ResponseEntity.ok(ApiResponse.success(batch));
  }
}
