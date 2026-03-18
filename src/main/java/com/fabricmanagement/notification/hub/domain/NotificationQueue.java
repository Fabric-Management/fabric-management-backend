package com.fabricmanagement.notification.hub.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * Gönderim kuyruğu — her bildirim için bir kayıt. Scheduler bu tabloyu periyodik işler. Max 3 retry
 * → FAILED.
 */
@Entity
@Table(schema = "notification", name = "notification_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationQueue extends BaseEntity {

  @Column(name = "recipient_id", nullable = false)
  private UUID recipientId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationImportance importance = NotificationImportance.NORMAL;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_type", nullable = false, length = 20)
  private NotificationDeliveryType deliveryType = NotificationDeliveryType.INSTANT;

  @Column(name = "scheduled_at")
  private Instant scheduledAt;

  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "JSONB", nullable = false)
  private Map<String, String> payload = Map.of();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationQueueStatus status = NotificationQueueStatus.PENDING;

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @Column(nullable = false, length = 10)
  private String locale = "TR";

  @Column(name = "processed_at")
  private Instant processedAt;

  @Override
  protected String getModuleCode() {
    return "NQ";
  }

  public static NotificationQueue create(
      UUID tenantId,
      UUID recipientId,
      String eventType,
      NotificationChannel channel,
      NotificationImportance importance,
      NotificationDeliveryType deliveryType,
      Map<String, String> payload,
      String locale) {
    var q = new NotificationQueue();
    q.setTenantId(tenantId);
    q.recipientId = recipientId;
    q.eventType = eventType;
    q.channel = channel;
    q.importance = importance;
    q.deliveryType = deliveryType;
    q.payload = payload != null ? payload : Map.of();
    q.locale = locale != null ? locale : "TR";
    q.status = NotificationQueueStatus.PENDING;
    return q;
  }

  /** Gönderim başladı — durum güncelle. */
  public void markProcessing() {
    this.status = NotificationQueueStatus.PROCESSING;
  }

  /** Başarıyla gönderildi. */
  public void markSent() {
    this.status = NotificationQueueStatus.SENT;
    this.processedAt = Instant.now();
  }

  /** Gönderim başarısız — retry sayısını artır. Max 3'te FAILED. */
  public void markFailed(String error) {
    this.retryCount++;
    this.lastError = error;
    if (this.retryCount >= 3) {
      this.status = NotificationQueueStatus.FAILED;
    } else {
      this.status = NotificationQueueStatus.PENDING;
    }
  }
}
