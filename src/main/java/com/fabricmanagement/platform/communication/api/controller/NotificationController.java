package com.fabricmanagement.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PageRequestDto;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.platform.communication.app.InAppNotificationService;
import com.fabricmanagement.platform.communication.dto.NotificationDto;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Notification API - User's own notifications.
 *
 * <p>Users can only see and manage their own notifications (recipientId = current user).
 */
@RestController("commonNotificationController")
@RequestMapping("/api/common/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

  private final InAppNotificationService inAppNotificationService;

  /**
   * List notifications for the current user (paginated).
   *
   * <p>GET /api/common/notifications
   *
   * <p>Query params: page, size, unread (optional, true = only unread)
   */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<PagedResponse<NotificationDto>>> list(
      @Valid PageRequestDto pageRequest, @RequestParam(required = false) Boolean unread) {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("User ID not set in context");
    }

    var page =
        inAppNotificationService.listForRecipient(
            userId, unread, pageRequest.toPageable(Sort.by(Sort.Direction.DESC, "createdAt")));

    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page, NotificationDto::from)));
  }

  /**
   * Get unread notification count for the current user.
   *
   * <p>GET /api/common/notifications/unread-count
   */
  @GetMapping("/unread-count")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("User ID not set in context");
    }

    long count = inAppNotificationService.getUnreadCount(userId);
    return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
  }

  /**
   * Mark a notification as read.
   *
   * <p>PATCH /api/common/notifications/{id}/read
   */
  @PatchMapping("/{id}/read")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("User ID not set in context");
    }

    inAppNotificationService.markAsRead(id, userId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Mark all notifications as read for the current user.
   *
   * <p>PATCH /api/common/notifications/read-all
   */
  @PatchMapping("/read-all")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Void> markAllAsRead() {
    UUID userId = TenantContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("User ID not set in context");
    }

    inAppNotificationService.markAllAsRead(userId);
    return ResponseEntity.noContent().build();
  }
}
