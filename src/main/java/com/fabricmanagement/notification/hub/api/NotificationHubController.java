package com.fabricmanagement.notification.hub.api;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.dto.NotificationLogResponse;
import com.fabricmanagement.notification.hub.dto.UpdateNotificationPreferenceRequest;
import com.fabricmanagement.notification.hub.infra.repository.NotificationLogRepository;
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
 * <p>GET /api/notifications — Kullanıcının bildirimleri (sayfalı) GET
 * /api/notifications/unread/count — Okunmamış sayı POST /api/notifications/{id}/read — Bir
 * bildirimi oku POST /api/notifications/read-all — Tümünü oku PUT /api/notifications/preferences —
 * Kanal tercihleri güncelle
 */
@RestController("notifHubController")
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Bildirim yönetimi ve tercihler")
public class NotificationHubController {

  private final NotificationHubService notificationHubService;
  private final NotificationLogRepository logRepo;

  @GetMapping
  @Operation(summary = "Kullanıcının bildirimlerini listele")
  public ApiResponse<PagedResponse<NotificationLogResponse>> listNotifications(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    UUID userId = currentUser().userId();
    var pageable = PageRequest.of(page, size, Sort.by("sentAt").descending());
    var result = logRepo.findByRecipient(userId, pageable).map(NotificationLogResponse::from);
    return ApiResponse.success(PagedResponse.from(result));
  }

  @GetMapping("/unread/count")
  @Operation(summary = "Okunmamış bildirim sayısı")
  public ApiResponse<Long> unreadCount() {
    return ApiResponse.success(notificationHubService.countUnread(currentUser().userId()));
  }

  @PostMapping("/{id}/read")
  @Operation(summary = "Bir bildirimi okundu işaretle")
  public ApiResponse<Void> markRead(@PathVariable UUID id) {
    notificationHubService.markRead(id, currentUser().userId());
    return ApiResponse.success(null);
  }

  @PostMapping("/read-all")
  @Operation(summary = "Tüm bildirimleri okundu işaretle")
  public ApiResponse<Integer> markAllRead() {
    int count = notificationHubService.markAllRead(currentUser().userId());
    return ApiResponse.success(count);
  }

  @PutMapping("/preferences")
  @Operation(summary = "Bildirim kanalı tercihini güncelle")
  public ResponseEntity<Void> updatePreference(
      @Valid @RequestBody UpdateNotificationPreferenceRequest req) {
    var ctx = currentUser();
    notificationHubService.updatePreference(
        ctx.tenantId(), ctx.userId(), req.eventType(), req.inApp(), req.email(), req.push());
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
