package com.fabricmanagement.production.masterdata.fiber.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PageRequestDto;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.production.masterdata.fiber.app.FiberRequestService;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequestRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberRequestDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Fiber request API - Tenant endpoints (submit, list own requests).
 *
 * <p>Security: FIBER WRITE for submit, FIBER READ for list/get.
 */
@RestController
@RequestMapping("/api/production/fiber-requests")
@RequiredArgsConstructor
@Slf4j
public class FiberRequestController {

  private final FiberRequestService fiberRequestService;

  /**
   * Submit a fiber request (tenant → platform).
   *
   * <p>POST /api/production/fiber-requests
   *
   * <p>Requires FIBER WRITE.
   */
  @PostMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'WRITE')")
  public ResponseEntity<ApiResponse<FiberRequestDto>> submit(
      @Valid @RequestBody CreateFiberRequestRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    log.info(
        "Submitting fiber request: isoCode={}, fiberName={}",
        request.getIsoCode(),
        request.getFiberName());
    FiberRequestDto created = fiberRequestService.submit(request, tenantId, userId);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(created, "Fiber request submitted successfully"));
  }

  /**
   * List own fiber requests (paginated).
   *
   * <p>GET /api/production/fiber-requests
   *
   * <p>Requires FIBER READ.
   */
  @GetMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<PagedResponse<FiberRequestDto>>> list(
      @Valid PageRequestDto pageRequest) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    var page =
        fiberRequestService.listByTenant(
            tenantId, pageRequest.toPageable(Sort.by(Sort.Direction.DESC, "createdAt")));

    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
  }

  /**
   * Get fiber request detail (own tenant only).
   *
   * <p>GET /api/production/fiber-requests/{id}
   *
   * <p>Requires FIBER READ.
   */
  @GetMapping("/{id}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'FIBER', 'READ')")
  public ResponseEntity<ApiResponse<FiberRequestDto>> getById(@PathVariable("id") UUID id) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    return ResponseEntity.ok(
        ApiResponse.success(
            fiberRequestService
                .getByIdForTenant(tenantId, id)
                .orElseThrow(() -> new EntityNotFoundException("Fiber request not found: " + id))));
  }
}
