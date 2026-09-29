package com.fabricmanagement.product.fiber.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.product.fiber.app.FiberQualityStandardService;
import com.fabricmanagement.product.fiber.dto.CreateFiberQualityStandardRequest;
import com.fabricmanagement.product.fiber.dto.FiberApplicableQualityStandardsDto;
import com.fabricmanagement.product.fiber.dto.FiberApplicableQualityStandardsRequest;
import com.fabricmanagement.product.fiber.dto.FiberQualityStandardDto;
import com.fabricmanagement.product.fiber.dto.FiberQualityStandardGroupDto;
import com.fabricmanagement.product.fiber.dto.UpdateFiberQualityStandardRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * REST API for tenant fibre quality profiles (FIBER-CATALOG-1): each profile targets a shared ISO
 * code (pure fibres of that ISO) or one exact fibre/mixture. GET and the read-only applicability
 * query require FIBER READ; create/update/delete require FIBER WRITE.
 */
@RestController
@RequestMapping("/api/v1/production/fiber-quality-standards")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Fiber Quality Standard", description = "Fiber Quality Standard operations")
public class FiberQualityStandardController {

  private final FiberQualityStandardService standardService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<List<FiberQualityStandardGroupDto>>> getAll() {
    return ResponseEntity.ok(ApiResponse.success(standardService.getAllGrouped()));
  }

  @GetMapping("/iso-code/{isoCodeId}")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<List<FiberQualityStandardDto>>> getByIsoCodeId(
      @PathVariable UUID isoCodeId) {
    return ResponseEntity.ok(ApiResponse.success(standardService.getByIsoCodeId(isoCodeId)));
  }

  @Operation(
      operationId = "getFiberQualityStandardsByFiber",
      summary = "Active quality profiles targeting one visible fibre (FIBER target)")
  @GetMapping("/fiber/{fiberId}")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<List<FiberQualityStandardDto>>> getByFiberId(
      @PathVariable UUID fiberId) {
    return ResponseEntity.ok(ApiResponse.success(standardService.getByFiberId(fiberId)));
  }

  @Operation(
      operationId = "getApplicableFiberQualityStandards",
      summary =
          "Read-only: profiles applicable to a fibre product and effective composition, with the"
              + " default the batch would use. Persists nothing")
  @PostMapping("/applicable")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<FiberApplicableQualityStandardsDto>> getApplicable(
      @Valid @RequestBody FiberApplicableQualityStandardsRequest request) {
    return ResponseEntity.ok(ApiResponse.success(standardService.getApplicable(request)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'fiber', 'write')")
  public ResponseEntity<ApiResponse<FiberQualityStandardDto>> create(
      @Valid @RequestBody CreateFiberQualityStandardRequest request) {
    FiberQualityStandardDto standard = standardService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(standard));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'write')")
  public ResponseEntity<ApiResponse<FiberQualityStandardDto>> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateFiberQualityStandardRequest request) {
    FiberQualityStandardDto standard = standardService.update(id, request);
    return ResponseEntity.ok(ApiResponse.success(standard));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'write')")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
    String warning = standardService.delete(id);
    return ResponseEntity.ok(
        ApiResponse.success(null, warning != null ? warning : "Quality standard deleted"));
  }
}
