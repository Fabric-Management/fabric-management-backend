package com.fabricmanagement.production.execution.fiber.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.execution.fiber.app.FiberBatchService;
import com.fabricmanagement.production.execution.fiber.dto.CreateFiberBatchRequest;
import com.fabricmanagement.production.execution.fiber.dto.FiberBatchDto;
import com.fabricmanagement.production.execution.fiber.dto.QuantityRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for managing fiber batches with production-ready reservation logic.
 */
@RestController
@RequestMapping("/api/production/batches/fiber")
@RequiredArgsConstructor
@Slf4j
public class FiberBatchController {
    
    private final FiberBatchService fiberBatchService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCTION_MANAGER', 'WAREHOUSE_MANAGER')")
    public ResponseEntity<ApiResponse<FiberBatchDto>> createBatch(@Valid @RequestBody CreateFiberBatchRequest request) {
        FiberBatchDto batch = fiberBatchService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(batch));
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCTION_MANAGER', 'WAREHOUSE_MANAGER')")
    public ResponseEntity<ApiResponse<List<FiberBatchDto>>> getAllBatches() {
        List<FiberBatchDto> batches = fiberBatchService.getAll();
        return ResponseEntity.ok(ApiResponse.success(batches));
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCTION_MANAGER', 'WAREHOUSE_MANAGER')")
    public ResponseEntity<ApiResponse<FiberBatchDto>> getBatch(@PathVariable UUID id) {
        return fiberBatchService.getById(id)
            .map(batch -> ResponseEntity.ok(ApiResponse.success(batch)))
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/fiber/{fiberId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCTION_MANAGER', 'WAREHOUSE_MANAGER')")
    public ResponseEntity<ApiResponse<List<FiberBatchDto>>> getBatchesByFiberId(@PathVariable UUID fiberId) {
        List<FiberBatchDto> batches = fiberBatchService.getByFiberId(fiberId);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }
    
    @PostMapping("/{id}/reserve")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCTION_MANAGER')")
    public ResponseEntity<ApiResponse<FiberBatchDto>> reserve(
            @PathVariable UUID id,
            @Valid @RequestBody QuantityRequest request) {
        FiberBatchDto batch = fiberBatchService.reserve(id, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success(batch));
    }
    
    @PostMapping("/{id}/release")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCTION_MANAGER')")
    public ResponseEntity<ApiResponse<FiberBatchDto>> release(
            @PathVariable UUID id,
            @Valid @RequestBody QuantityRequest request) {
        FiberBatchDto batch = fiberBatchService.release(id, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success(batch));
    }
    
    @PostMapping("/{id}/consume")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRODUCTION_MANAGER')")
    public ResponseEntity<ApiResponse<FiberBatchDto>> consume(
            @PathVariable UUID id,
            @Valid @RequestBody QuantityRequest request) {
        FiberBatchDto batch = fiberBatchService.consume(id, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success(batch));
    }
}
