package com.fabricmanagement.production.masterdata.fiber.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.masterdata.fiber.app.FiberService;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberAttributeDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCatalogSummaryDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCategoryDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberCertificationDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberIsoCodeDto;
import com.fabricmanagement.production.masterdata.fiber.dto.UpdateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberAttributeRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCategoryRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCertificationRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberIsoCodeRepository;
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
 * Fiber Controller - REST API for fiber management.
 *
 * <p>Security uses department-aware checks via {@code ProductionAccessService}. WRITE = create /
 * update / deactivate (ADMIN, or MANAGER in R&D / Prod. Planning / Fiber dept). READ = any
 * authenticated user in a production-related department.
 */
@RestController
@RequestMapping("/api/production/fibers")
@RequiredArgsConstructor
@Slf4j
public class FiberController {

  private final FiberService fiberService;
  private final FiberCategoryRepository fiberCategoryRepository;
  private final FiberIsoCodeRepository fiberIsoCodeRepository;
  private final FiberAttributeRepository fiberAttributeRepository;
  private final FiberCertificationRepository fiberCertificationRepository;

  @PostMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberDto>> createFiber(
      @Valid @RequestBody CreateFiberRequest request) {
    boolean isBlended = request.getComposition() != null && !request.getComposition().isEmpty();
    log.info(
        "Creating {} fiber: name={}, materialId={}, unit={}",
        isBlended ? "blended" : "pure",
        request.getFiberName(),
        request.getMaterialId(),
        request.getUnit());

    // USER-FRIENDLY: Material will be auto-created if materialId is null
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
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<FiberDto>> getFiber(@PathVariable("id") UUID id) {
    return fiberService
        .getById(id)
        .map(fiber -> ResponseEntity.ok(ApiResponse.success(fiber)))
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/material/{materialId}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<FiberDto>> getFiberByMaterial(@PathVariable UUID materialId) {
    return fiberService
        .getByMaterialId(materialId)
        .map(fiber -> ResponseEntity.ok(ApiResponse.success(fiber)))
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberDto>>> getAllFibers() {
    List<FiberDto> fibers = fiberService.getAll();
    return ResponseEntity.ok(ApiResponse.success(fibers));
  }

  /**
   * Single response with categories, ISO codes, attributes, certifications, and fibers (tenant +
   * platform seed).
   */
  @GetMapping("/catalog-summary")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<FiberCatalogSummaryDto>> getCatalogSummary() {
    FiberCatalogSummaryDto summary = fiberService.getCatalogSummary();
    return ResponseEntity.ok(ApiResponse.success(summary));
  }

  @GetMapping("/search")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberDto>>> searchFibers(@RequestParam String name) {
    List<FiberDto> fibers = fiberService.searchByName(name);
    return ResponseEntity.ok(ApiResponse.success(fibers));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberDto>> updateFiber(
      @PathVariable UUID id, @Valid @RequestBody UpdateFiberRequest request) {
    log.info("Updating fiber: id={}", id);

    FiberDto fiber = fiberService.updateFiber(id, request);

    return ResponseEntity.ok(ApiResponse.success(fiber, "Fiber updated successfully"));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'WRITE')")
  public ResponseEntity<ApiResponse<Void>> deactivateFiber(@PathVariable UUID id) {
    fiberService.deactivateFiber(id);
    return ResponseEntity.ok(ApiResponse.success(null, "Fiber deactivated successfully"));
  }

  @GetMapping("/categories")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberCategoryDto>>> getCategories() {
    List<FiberCategoryDto> categories =
        fiberCategoryRepository.findByIsActiveTrue().stream().map(FiberCategoryDto::from).toList();
    return ResponseEntity.ok(ApiResponse.success(categories));
  }

  @GetMapping("/iso-codes")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberIsoCodeDto>>> getIsoCodes() {
    List<FiberIsoCodeDto> isoCodes =
        fiberIsoCodeRepository.findByIsActiveTrue().stream().map(FiberIsoCodeDto::from).toList();
    return ResponseEntity.ok(ApiResponse.success(isoCodes));
  }

  @GetMapping("/attributes")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberAttributeDto>>> getAttributes() {
    List<FiberAttributeDto> attributes =
        fiberAttributeRepository.findByIsActiveTrue().stream()
            .map(FiberAttributeDto::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(attributes));
  }

  @GetMapping("/certifications")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<List<FiberCertificationDto>>> getCertifications() {
    List<FiberCertificationDto> certifications =
        fiberCertificationRepository.findByIsActiveTrue().stream()
            .map(FiberCertificationDto::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(certifications));
  }
}
