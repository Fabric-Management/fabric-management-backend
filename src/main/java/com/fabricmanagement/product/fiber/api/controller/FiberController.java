package com.fabricmanagement.product.fiber.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.product.fiber.app.FiberCatalogQueryService;
import com.fabricmanagement.product.fiber.app.FiberIsoCodeService;
import com.fabricmanagement.product.fiber.app.FiberReferenceQueryService;
import com.fabricmanagement.product.fiber.app.FiberService;
import com.fabricmanagement.product.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.product.fiber.dto.FiberCatalogSummaryDto;
import com.fabricmanagement.product.fiber.dto.FiberCategoryDto;
import com.fabricmanagement.product.fiber.dto.FiberCertificationDto;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.fiber.dto.FiberIsoCodeDto;
import com.fabricmanagement.product.fiber.dto.UpdateFiberRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
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
 * Fiber Controller - REST API for fiber management (FIBER-CATALOG-1: shared catalogue + tenant
 * blends; shared rows are read-only for tenants and carry backend capabilities).
 *
 * <p>Security uses department-aware checks via {@code PermissionEvaluator}. WRITE = create / update
 * / deactivate (ADMIN, or MANAGER in R&D / Prod. Planning / Fiber dept). READ = any authenticated
 * user in a production-related department.
 */
@RestController
@RequestMapping("/api/v1/production/fibers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fiber", description = "Fiber operations")
public class FiberController {

  private final FiberService fiberService;
  private final FiberCatalogQueryService fiberCatalogQueryService;
  private final FiberIsoCodeService fiberIsoCodeService;
  private final FiberReferenceQueryService referenceQueryService;

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'fiber', 'write')")
  public ResponseEntity<ApiResponse<FiberDto>> createFiber(
      @Valid @RequestBody CreateFiberRequest request) {
    boolean isBlended = request.getComposition() != null && !request.getComposition().isEmpty();
    log.info(
        "Creating {} fiber: name={}, productId={}, unit={}",
        isBlended ? "blended" : "pure",
        request.getFiberName(),
        request.getProductId(),
        request.getUnit());

    // USER-FRIENDLY: Product will be auto-created if productId is null
    // Unified endpoint: composition field determines if pure or blended
    // - Pure fiber: composition is null or empty
    // - Blended fiber: composition contains base fiber IDs with percentages

    FiberDto fiber = fiberService.createFiber(request);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                fiber,
                isBlended ? "Blended fiber created successfully" : "Fiber created successfully"));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<FiberDto>> getFiber(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(
        ApiResponse.success(
            fiberService
                .getById(id)
                .orElseThrow(() -> new EntityNotFoundException("Fiber not found: " + id))));
  }

  @GetMapping("/product/{productId}")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<FiberDto>> getFiberByProduct(@PathVariable UUID productId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            fiberService
                .getByProductId(productId)
                .orElseThrow(
                    () ->
                        new EntityNotFoundException("Fiber not found for product: " + productId))));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<List<FiberDto>>> getAllFibers() {
    List<FiberDto> fibers = fiberService.getAll();
    return ResponseEntity.ok(ApiResponse.success(fibers));
  }

  /**
   * Single response with categories, ISO codes, attributes, certifications, and fibers (tenant +
   * platform seed).
   */
  @GetMapping("/catalog-summary")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<FiberCatalogSummaryDto>> getCatalogSummary() {
    FiberCatalogSummaryDto summary = fiberCatalogQueryService.getCatalogSummary();
    return ResponseEntity.ok(ApiResponse.success(summary));
  }

  @GetMapping("/search")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<List<FiberDto>>> searchFibers(@RequestParam String name) {
    List<FiberDto> fibers = fiberService.searchByName(name);
    return ResponseEntity.ok(ApiResponse.success(fibers));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'write')")
  public ResponseEntity<ApiResponse<FiberDto>> updateFiber(
      @PathVariable UUID id, @Valid @RequestBody UpdateFiberRequest request) {
    log.info("Updating fiber: id={}", id);

    FiberDto fiber = fiberService.updateFiber(id, request);

    return ResponseEntity.ok(ApiResponse.success(fiber, "Fiber updated successfully"));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'write')")
  public ResponseEntity<ApiResponse<Void>> deactivateFiber(@PathVariable UUID id) {
    fiberService.deactivateFiber(id);
    return ResponseEntity.ok(ApiResponse.success(null, "Fiber deactivated successfully"));
  }

  @GetMapping("/categories")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<List<FiberCategoryDto>>> getCategories() {
    return ResponseEntity.ok(ApiResponse.success(referenceQueryService.listCategories()));
  }

  /**
   * Shared ISO codes (one row per code, catalogue owner). {@code baseOnly=true} keeps only codes
   * classified as official in the existing catalogue metadata.
   */
  @GetMapping("/iso-codes")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<List<FiberIsoCodeDto>>> getIsoCodes(
      @RequestParam(defaultValue = "false") boolean baseOnly) {
    List<FiberIsoCodeDto> isoCodes = fiberIsoCodeService.getIsoCodes(baseOnly);
    return ResponseEntity.ok(ApiResponse.success(isoCodes));
  }

  @GetMapping("/certifications")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<List<FiberCertificationDto>>> getCertifications() {
    return ResponseEntity.ok(ApiResponse.success(referenceQueryService.listCertificationSchemes()));
  }
}
