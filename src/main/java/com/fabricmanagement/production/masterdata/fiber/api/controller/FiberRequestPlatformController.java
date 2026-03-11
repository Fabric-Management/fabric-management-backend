package com.fabricmanagement.production.masterdata.fiber.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PageRequestDto;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.production.masterdata.fiber.app.FiberRequestService;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberRequestStatus;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberRequestDto;
import com.fabricmanagement.production.masterdata.fiber.dto.RejectFiberRequestRequest;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Fiber request API - Platform endpoints (list pending, approve, reject).
 *
 * <p>Security: All endpoints require PLATFORM_ADMIN role.
 */
@RestController
@RequestMapping("/api/platform/fiber-requests")
@RequiredArgsConstructor
@Slf4j
public class FiberRequestPlatformController {

  private final FiberRequestService fiberRequestService;

  /**
   * List fiber requests (paginated, optional status filter).
   *
   * <p>GET /api/platform/fiber-requests?status=PENDING|APPROVED|REJECTED
   *
   * <p>Omit status or use status=ALL for all requests.
   *
   * <p>Requires PLATFORM_ADMIN.
   */
  @GetMapping
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<PagedResponse<FiberRequestDto>>> list(
      @RequestParam(required = false) String status, @Valid PageRequestDto pageRequest) {
    Optional<FiberRequestStatus> statusFilter = parseStatus(status);
    if (statusFilter == null) {
      return ResponseEntity.badRequest().build();
    }
    var page =
        fiberRequestService.listForPlatform(
            statusFilter, pageRequest.toPageable(Sort.by(Sort.Direction.DESC, "createdAt")));

    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
  }

  /**
   * @return Optional.empty() for all/blank, Optional.of(status) for valid status, null for invalid
   *     status (caller should return 400)
   */
  private Optional<FiberRequestStatus> parseStatus(String status) {
    if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
      return Optional.empty();
    }
    try {
      return Optional.of(FiberRequestStatus.valueOf(status.toUpperCase()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Get fiber request detail (any tenant).
   *
   * <p>GET /api/platform/fiber-requests/{id}
   *
   * <p>Requires PLATFORM_ADMIN.
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<FiberRequestDto>> getById(@PathVariable("id") UUID id) {
    return fiberRequestService
        .getById(id)
        .map(dto -> ResponseEntity.ok(ApiResponse.success(dto)))
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Approve a fiber request.
   *
   * <p>POST /api/platform/fiber-requests/{id}/approve
   *
   * <p>Requires PLATFORM_ADMIN.
   */
  @PostMapping("/{id}/approve")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<FiberRequestDto>> approve(@PathVariable("id") UUID id) {
    UUID reviewedBy = TenantContext.getCurrentUserId();
    if (reviewedBy == null) {
      return ResponseEntity.status(401).build();
    }

    log.info("Platform approving fiber request: id={}", id);
    FiberRequestDto approved = fiberRequestService.approve(id, reviewedBy);

    return ResponseEntity.ok(ApiResponse.success(approved, "Fiber request approved"));
  }

  /**
   * Reject a fiber request (reviewNote required, min 10 chars).
   *
   * <p>POST /api/platform/fiber-requests/{id}/reject
   *
   * <p>Requires PLATFORM_ADMIN.
   */
  @PostMapping("/{id}/reject")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<FiberRequestDto>> reject(
      @PathVariable("id") UUID id, @Valid @RequestBody RejectFiberRequestRequest request) {
    UUID reviewedBy = TenantContext.getCurrentUserId();
    if (reviewedBy == null) {
      return ResponseEntity.status(401).build();
    }

    log.info("Platform rejecting fiber request: id={}", id);
    FiberRequestDto rejected = fiberRequestService.reject(id, request.getReviewNote(), reviewedBy);

    return ResponseEntity.ok(ApiResponse.success(rejected, "Fiber request rejected"));
  }
}
