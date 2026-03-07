package com.fabricmanagement.production.masterdata.fiber.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.masterdata.fiber.app.FiberSpecificationService;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberSpecificationRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberSpecificationDto;
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
 * REST API for fiber quality specifications (target tolerances).
 *
 * <p>Manages min/target/max specification profiles per fiber. These define the expected quality
 * parameters used to compare against actual {@code FiberTestResult} values.
 */
@RestController
@RequestMapping("/api/production/fibers/specifications")
@RequiredArgsConstructor
@Slf4j
public class FiberSpecificationController {

  private final FiberSpecificationService specService;

  @PostMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberSpecificationDto>> createSpec(
      @Valid @RequestBody CreateFiberSpecificationRequest request) {
    FiberSpecificationDto spec = specService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(spec));
  }

  @GetMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberSpecificationDto>>> getAll() {
    return ResponseEntity.ok(ApiResponse.success(specService.getAll()));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<FiberSpecificationDto>> getById(@PathVariable UUID id) {
    return specService
        .getById(id)
        .map(spec -> ResponseEntity.ok(ApiResponse.success(spec)))
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/fiber/{fiberId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberSpecificationDto>>> getByFiberId(
      @PathVariable UUID fiberId) {
    return ResponseEntity.ok(ApiResponse.success(specService.getByFiberId(fiberId)));
  }

  @GetMapping("/fiber/{fiberId}/default")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<FiberSpecificationDto>> getDefaultSpec(
      @PathVariable UUID fiberId) {
    return specService
        .getDefaultSpec(fiberId)
        .map(spec -> ResponseEntity.ok(ApiResponse.success(spec)))
        .orElse(ResponseEntity.notFound().build());
  }

  @PatchMapping("/{id}/set-default")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberSpecificationDto>> setDefault(@PathVariable UUID id) {
    FiberSpecificationDto spec = specService.setDefault(id);
    return ResponseEntity.ok(ApiResponse.success(spec, "Default specification updated"));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'WRITE')")
  public ResponseEntity<ApiResponse<Void>> deleteSpec(@PathVariable UUID id) {
    specService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null, "Specification deleted"));
  }
}
