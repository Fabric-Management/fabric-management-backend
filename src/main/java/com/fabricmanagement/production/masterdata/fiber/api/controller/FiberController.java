package com.fabricmanagement.production.masterdata.fiber.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateBlendedFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.fiber.app.FiberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Fiber Controller - REST API for fiber management.
 */
@RestController
@RequestMapping("/api/production/fibers")
@RequiredArgsConstructor
@Slf4j
public class FiberController {

    private final FiberService fiberService;

    @PostMapping
    public ResponseEntity<ApiResponse<FiberDto>> createFiber(
            @Valid @RequestBody CreateFiberRequest request) {
        log.info("Creating fiber: name={}", request.getFiberName());
        
        FiberDto fiber = fiberService.createFiber(request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(fiber, "Fiber created successfully"));
    }

    @PostMapping("/blended")
    public ResponseEntity<ApiResponse<FiberDto>> createBlendedFiber(
            @Valid @RequestBody CreateBlendedFiberRequest request) {
        log.info("Creating blended fiber: name={}, composition={}", 
            request.getFiberName(), request.getComposition());
        
        FiberDto fiber = fiberService.createBlendedFiber(request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(fiber, "Blended fiber created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FiberDto>> getFiber(@PathVariable UUID id) {
        return fiberService.getById(id)
            .map(fiber -> ResponseEntity.ok(ApiResponse.success(fiber)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/material/{materialId}")
    public ResponseEntity<ApiResponse<FiberDto>> getFiberByMaterial(@PathVariable UUID materialId) {
        return fiberService.getByMaterialId(materialId)
            .map(fiber -> ResponseEntity.ok(ApiResponse.success(fiber)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FiberDto>>> getAllFibers() {
        List<FiberDto> fibers = fiberService.getAll();
        return ResponseEntity.ok(ApiResponse.success(fibers));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FiberDto>> updateFiber(
            @PathVariable UUID id,
            @Valid @RequestBody CreateFiberRequest request) {
        log.info("Updating fiber: id={}", id);
        
        FiberDto fiber = fiberService.updateFiber(id, request);
        
        return ResponseEntity.ok(ApiResponse.success(fiber, "Fiber updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateFiber(@PathVariable UUID id) {
        fiberService.deactivateFiber(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Fiber deactivated successfully"));
    }
}
