package com.fabricmanagement.common.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PageRequestDto;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.common.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.common.platform.communication.dto.NotificationDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Platform notification API - For PLATFORM_ADMIN users.
 *
 * <p>Lists notifications with tenant_id = SYSTEM_TENANT_ID (platform-level events like
 * FIBER_REQUEST_SUBMITTED, NEW_TENANT_ONBOARDED).
 */
@RestController
@RequestMapping("/api/platform/notifications")
@RequiredArgsConstructor
@Slf4j
public class PlatformNotificationController {

  private final InAppNotificationService inAppNotificationService;

  /**
   * List platform notifications (paginated).
   *
   * <p>GET /api/platform/notifications
   *
   * <p>Requires PLATFORM_ADMIN role.
   */
  @GetMapping
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  public ResponseEntity<ApiResponse<PagedResponse<NotificationDto>>> list(
      @Valid PageRequestDto pageRequest) {
    log.debug("Platform admin: Listing platform notifications");

    var page =
        inAppNotificationService.listPlatform(
            pageRequest.toPageable(Sort.by(Sort.Direction.DESC, "createdAt")));

    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page, NotificationDto::from)));
  }
}
