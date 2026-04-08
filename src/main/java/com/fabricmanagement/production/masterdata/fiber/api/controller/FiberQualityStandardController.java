package com.fabricmanagement.production.masterdata.fiber.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.masterdata.fiber.app.FiberQualityStandardService;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberQualityStandardRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberQualityStandardDto;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberQualityStandardGroupDto;
import com.fabricmanagement.production.masterdata.fiber.dto.UpdateFiberQualityStandardRequest;
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
 * REST API for fiber quality standards (ISO code based target tolerances).
 *
 * <p>Manages min/target/max standard profiles per ISO code. GET requires FIBER READ;
 * create/update/delete require FIBER WRITE.
 */
@RestController
@RequestMapping("/api/production/fiber-quality-standards")
@RequiredArgsConstructor
@Slf4j
public class FiberQualityStandardController {

  private final FiberQualityStandardService standardService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<List<FiberQualityStandardGroupDto>>> getAll() {
    return ResponseEntity.ok(ApiResponse.success(standardService.getAllGroupedByIsoCode()));
  }

  @GetMapping("/iso-code/{isoCodeId}")
  @PreAuthorize("@auth.can(authentication, 'fiber', 'read')")
  public ResponseEntity<ApiResponse<List<FiberQualityStandardDto>>> getByIsoCodeId(
      @PathVariable UUID isoCodeId) {
    return ResponseEntity.ok(ApiResponse.success(standardService.getByIsoCodeId(isoCodeId)));
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
