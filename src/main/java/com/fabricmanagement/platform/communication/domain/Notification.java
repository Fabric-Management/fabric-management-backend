package com.fabricmanagement.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * In-app and email notification entity.
 *
 * <p>Supports platform-level (tenant_id = SYSTEM_TENANT_ID) and tenant-level notifications.
 * recipient_id nullable = broadcast to all admins of the tenant.
 */
@Entity
@Table(
    name = "common_notification",
    schema = "common_communication",
    indexes = {
      @Index(name = "idx_notif_tenant", columnList = "tenant_id"),
      @Index(name = "idx_notif_recipient", columnList = "recipient_id"),
      @Index(name = "idx_notif_read", columnList = "is_read"),
      @Index(name = "idx_notif_created_at", columnList = "created_at")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

  /** Target user; null = broadcast to all admins of the tenant (tenantId from BaseEntity) */
  @Column(name = "recipient_id")
  private UUID recipientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 50)
  private NotificationType type;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "message", nullable = false, columnDefinition = "text")
  private String message;

  /** Related entity UUID (e.g. fiber request, batch) */
  @Column(name = "reference_id")
  private UUID referenceId;

  /** Reference type: FIBER_REQUEST, BATCH, QC, etc. */
  @Column(name = "reference_type", length = 50)
  private String referenceType;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, length = 20)
  @Builder.Default
  private NotificationDeliveryChannel channel = NotificationDeliveryChannel.IN_APP;

  @Column(name = "is_read", nullable = false)
  @Builder.Default
  private Boolean isRead = false;

  @Column(name = "read_at")
  private Instant readAt;

  @Override
  protected String getModuleCode() {
    return "NOTIF";
  }

  /** Mark notification as read */
  public void markAsRead() {
    this.isRead = true;
    this.readAt = Instant.now();
  }
}
