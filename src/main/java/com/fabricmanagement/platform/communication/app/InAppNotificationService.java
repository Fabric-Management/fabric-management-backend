package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.communication.domain.Notification;
import com.fabricmanagement.platform.communication.domain.NotificationDeliveryChannel;
import com.fabricmanagement.platform.communication.domain.NotificationType;
import com.fabricmanagement.platform.communication.dto.NotificationRequest;
import com.fabricmanagement.platform.communication.infra.repository.NotificationRepository;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserContactRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notification service for creating and managing notifications.
 *
 * <p>Handles notification persistence, email delivery (when channel is EMAIL or BOTH), and read
 * status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationService {

  private static final Set<String> ADMIN_ROLE_CODES = Set.of("ADMIN", "PLATFORM_ADMIN");

  /** Roles notified when batch enters quarantine (TB-3: broadcast optimization later) */
  public static final Set<String> QUARANTINE_NOTIFY_ROLES = Set.of("ADMIN", "MANAGER");

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final UserContactRepository userContactRepository;
  private final NotificationService notificationService;
  private final TenantQueryPort tenantQueryPort;

  /**
   * Send notification: save to DB and optionally send email if channel is EMAIL or BOTH.
   *
   * @param request notification request
   * @return saved notification
   */
  @Transactional
  public Notification send(NotificationRequest request) {
    if (isPlaygroundTenant(request.tenantId())) {
      log.debug(
          "🛡️ [PLAYGROUND] Skipping in-app notification for playground tenant: {}",
          request.tenantId());
      return null;
    }

    Notification notification =
        TenantContext.executeInTenantContext(
            request.tenantId(),
            () -> {
              Notification n =
                  Notification.builder()
                      .recipientId(request.recipientId())
                      .type(request.type())
                      .title(request.title())
                      .message(request.message())
                      .referenceId(request.referenceId())
                      .referenceType(request.referenceType())
                      .channel(request.channel())
                      .build();
              return notificationRepository.save(n);
            });

    if (request.channel() == NotificationDeliveryChannel.EMAIL
        || request.channel() == NotificationDeliveryChannel.BOTH) {
      sendEmailAsync(request, notification);
    }

    return notification;
  }

  private void sendEmailAsync(NotificationRequest request, Notification notification) {
    List<String> emails = resolveRecipientEmails(request.tenantId(), request.recipientId());
    for (String email : emails) {
      notificationService.sendNotification(
          request.tenantId(), email, notification.getTitle(), notification.getMessage());
    }
  }

  private List<String> resolveRecipientEmails(UUID tenantId, UUID recipientId) {
    if (recipientId != null) {
      return getEmailForUser(tenantId, recipientId).stream().toList();
    }
    // Broadcast: all admin users of the tenant
    List<User> admins = userRepository.findByTenantIdAndRole_RoleCodeIn(tenantId, ADMIN_ROLE_CODES);
    return admins.stream()
        .flatMap(u -> getEmailForUser(tenantId, u.getId()).stream())
        .distinct()
        .collect(Collectors.toList());
  }

  private Optional<String> getEmailForUser(UUID tenantId, UUID userId) {
    return userContactRepository.findByTenantIdAndUserId(tenantId, userId).stream()
        .filter(uc -> uc.getContact() != null)
        .map(uc -> uc.getContact())
        .filter(c -> c.getContactType() == ContactType.EMAIL)
        .filter(c -> Boolean.TRUE.equals(c.getIsVerified()))
        .map(c -> c.getContactValue())
        .findFirst();
  }

  /**
   * Mark a notification as read. Only applies when recipientId matches (single-recipient
   * notifications). Throws AccessDeniedException if notification does not belong to user.
   */
  @Transactional
  public void markAsRead(UUID notificationId, UUID userId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
    if (notification.getRecipientId() == null || !notification.getRecipientId().equals(userId)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Notification does not belong to user");
    }
    notification.markAsRead();
    notificationRepository.save(notification);
  }

  /** Mark all notifications for a recipient as read. */
  @Transactional
  public void markAllAsRead(UUID recipientId) {
    List<Notification> unread =
        notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId);
    unread.forEach(Notification::markAsRead);
    notificationRepository.saveAll(unread);
  }

  /** Get unread count for a recipient. */
  @Transactional(readOnly = true)
  public long getUnreadCount(UUID recipientId) {
    return notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
  }

  /** List notifications for a tenant with pagination. */
  @Transactional(readOnly = true)
  public Page<Notification> list(UUID tenantId, Pageable pageable) {
    return notificationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
  }

  /**
   * List notifications for a recipient with optional unread filter.
   *
   * @param recipientId user ID
   * @param unreadOnly if true, only unread notifications; if false/null, all
   */
  @Transactional(readOnly = true)
  public Page<Notification> listForRecipient(
      UUID recipientId, Boolean unreadOnly, Pageable pageable) {
    if (Boolean.TRUE.equals(unreadOnly)) {
      return notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(
          recipientId, pageable);
    }
    return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);
  }

  /** List platform notifications (tenant_id = SYSTEM_TENANT_ID). */
  @Transactional(readOnly = true)
  public Page<Notification> listPlatform(Pageable pageable) {
    return notificationRepository.findByTenantIdOrderByCreatedAtDesc(
        TenantContext.SYSTEM_TENANT_ID, pageable);
  }

  /**
   * Send notification to each tenant user with the given roles. Creates one notification per
   * recipient so they appear in /api/common/notifications.
   *
   * @param tenantId tenant context
   * @param roleCodes roles to target (e.g. ADMIN, MANAGER)
   * @param type notification type
   * @param title notification title
   * @param message notification message
   * @param referenceId related entity ID (e.g. batch ID)
   * @param referenceType reference type (e.g. BATCH)
   * @param channel delivery channel
   */
  @Transactional
  public void sendToTenantRoles(
      UUID tenantId,
      Set<String> roleCodes,
      NotificationType type,
      String title,
      String message,
      UUID referenceId,
      String referenceType,
      NotificationDeliveryChannel channel) {
    List<User> recipients = userRepository.findByTenantIdAndRole_RoleCodeIn(tenantId, roleCodes);
    for (User user : recipients) {
      send(
          NotificationRequest.builder()
              .tenantId(tenantId)
              .recipientId(user.getId())
              .type(type)
              .title(title)
              .message(message)
              .referenceId(referenceId)
              .referenceType(referenceType)
              .channel(channel)
              .build());
    }
  }

  private boolean isPlaygroundTenant(UUID tenantId) {
    if (tenantId == null) {
      return false;
    }
    // Uses TenantQueryPort (BYPASSRLS)
    return tenantQueryPort
        .findById(tenantId)
        .map(ref -> "PLAYGROUND".equals(ref.type()))
        .orElse(false);
  }
}
