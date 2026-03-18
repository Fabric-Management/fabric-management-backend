package com.fabricmanagement.notification.hub.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Gönderilen bildirimlerin logu + kullanıcı etkileşim takibi. read, clicked, actionTaken alanları
 * frontend tarafından güncellenir.
 */
@Entity
@Table(schema = "notification", name = "notification_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog extends BaseEntity {

  @Column(name = "recipient_id", nullable = false)
  private UUID recipientId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationImportance importance;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(nullable = false, length = 10)
  private String locale;

  @Column(name = "sent_at", nullable = false)
  private Instant sentAt;

  @Column(name = "is_read", nullable = false)
  private boolean isRead = false;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "is_clicked", nullable = false)
  private boolean isClicked = false;

  @Column(name = "clicked_at")
  private Instant clickedAt;

  /** APPROVED / REJECTED / DISMISSED */
  @Column(name = "action_taken", length = 30)
  private String actionTaken;

  @Column(name = "action_taken_at")
  private Instant actionTakenAt;

  /** Gruplama için — aynı grup bildirimleri birleştirilir. */
  @Column(name = "group_id")
  private UUID groupId;

  @Column(name = "reference_id")
  private UUID referenceId;

  @Column(name = "reference_type", length = 100)
  private String referenceType;

  @Override
  protected String getModuleCode() {
    return "NL";
  }

  public static NotificationLog from(
      UUID tenantId,
      UUID recipientId,
      String eventType,
      NotificationChannel channel,
      NotificationImportance importance,
      String title,
      String body,
      String locale,
      UUID referenceId,
      String referenceType,
      UUID groupId) {
    var log = new NotificationLog();
    log.setTenantId(tenantId);
    log.recipientId = recipientId;
    log.eventType = eventType;
    log.channel = channel;
    log.importance = importance;
    log.title = title;
    log.body = body;
    log.locale = locale;
    log.sentAt = Instant.now();
    log.referenceId = referenceId;
    log.referenceType = referenceType;
    log.groupId = groupId;
    return log;
  }

  public void markRead() {
    this.isRead = true;
    this.readAt = Instant.now();
  }

  public void markClicked() {
    this.isClicked = true;
    this.clickedAt = Instant.now();
  }

  public void recordAction(String action) {
    this.actionTaken = action;
    this.actionTakenAt = Instant.now();
  }
}
