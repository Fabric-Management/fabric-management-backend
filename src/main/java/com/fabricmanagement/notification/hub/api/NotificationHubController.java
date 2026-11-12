package com.fabricmanagement.notification.hub.api;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.app.NotificationPreferenceService;
import com.fabricmanagement.notification.hub.domain.NotificationEvent;
import com.fabricmanagement.notification.hub.domain.NotificationGroup;
import com.fabricmanagement.notification.hub.dto.NotificationLogResponse;
import com.fabricmanagement.notification.hub.dto.NotificationPreferencesResponse;
import com.fabricmanagement.notification.hub.dto.UpdateEmailPreferenceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Bildirim REST API.
 *
 * <p>GET /api/v1/notifications — Kullanıcının uygulama içi bildirimleri (sayfalı, ?unread=true) GET
 * /api/v1/notifications/unread/count — Okunmamış sayı POST /api/v1/notifications/{id}/read — Bir
 * bildirimi oku POST /api/v1/notifications/read-all — Tümünü oku GET
 * /api/v1/notifications/preferences — Tercih kataloğu PUT
 * /api/v1/notifications/preferences/groups/{group} — Grup için e-posta PUT
 * /api/v1/notifications/preferences/events/{eventType} — Tek bildirim için e-posta
 */
@RestController("notifHubController")
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification management and preferences")
public class NotificationHubController {

  private final NotificationHubService notificationHubService;
  private final NotificationPreferenceService preferenceService;

  @GetMapping
  @Operation(summary = "List user notifications")
  public ApiResponse<PagedResponse<NotificationLogResponse>> listNotifications(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "false") boolean unread) {
    UUID userId = currentUser().userId();
    var pageable = PageRequest.of(page, size, Sort.by("sentAt").descending());
    var result =
        notificationHubService.list(userId, unread, pageable).map(NotificationLogResponse::from);
    return ApiResponse.success(PagedResponse.from(result));
  }

  @GetMapping("/unread/count")
  @Operation(summary = "Unread notification count")
  public ApiResponse<Long> unreadCount() {
    return ApiResponse.success(notificationHubService.countUnread(currentUser().userId()));
  }

  @PostMapping("/{id}/read")
  @Operation(summary = "Mark a notification as read")
  public ApiResponse<Void> markRead(@PathVariable UUID id) {
    notificationHubService.markRead(id, currentUser().userId());
    return ApiResponse.success(null);
  }

  @PostMapping("/read-all")
  @Operation(summary = "Mark all notifications as read")
  public ApiResponse<Integer> markAllRead() {
    int count = notificationHubService.markAllRead(currentUser().userId());
    return ApiResponse.success(count);
  }

  @GetMapping("/preferences")
  @Operation(
      summary = "Notification preferences",
      description =
          "What can be configured, grouped for the preferences screen, with the signed-in user's"
              + " current e-mail choice. In-app notifications are always delivered and are not"
              + " listed; critical ones are locked.")
  public ApiResponse<NotificationPreferencesResponse> preferences() {
    var ctx = currentUser();
    return ApiResponse.success(preferenceService.catalogue(ctx.tenantId(), ctx.userId()));
  }

  @PutMapping("/preferences/groups/{group}")
  @Operation(
      summary = "Set e-mail for a notification group",
      description = "Applies to every notification in the group that can be changed.")
  public ResponseEntity<Void> updateGroup(
      @PathVariable NotificationGroup group, @Valid @RequestBody UpdateEmailPreferenceRequest req) {
    var ctx = currentUser();
    preferenceService.setGroup(ctx.tenantId(), ctx.userId(), group, req.email());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/preferences/events/{eventType}")
  @Operation(
      summary = "Set e-mail for one notification",
      description = "Rejected for a critical notification, which is always sent.")
  public ResponseEntity<Void> updateEvent(
      @PathVariable NotificationEvent eventType,
      @Valid @RequestBody UpdateEmailPreferenceRequest req) {
    var ctx = currentUser();
    preferenceService.setEvent(ctx.tenantId(), ctx.userId(), eventType, req.email());
    return ResponseEntity.noContent().build();
  }

  // ---- Yardımcı ----

  private AuthenticatedUserContext currentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof AuthenticatedUserContext ctx) {
      return ctx;
    }
    throw new IllegalStateException("AuthenticatedUserContext not found in SecurityContext");
  }
}
