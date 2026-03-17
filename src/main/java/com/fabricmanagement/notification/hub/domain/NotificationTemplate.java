package com.fabricmanagement.notification.hub.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bildirim şablonu — event + kanal başına bir şablon.
 *
 * <p>titleKey ve bodyKey → i18n.translation_key.key_code referansları. Render ederken
 * TranslationService kullanılır + parametre replace yapılır.
 */
@Entity
@Table(
    schema = "notification",
    name = "notification_template",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "event_type", "channel"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationTemplate extends BaseEntity {

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationChannel channel;

  /** TranslationKey.keyCode referansı (önce i18n tablosuna kayıt edilmeli) */
  @Column(name = "title_key", nullable = false, length = 255)
  private String titleKey;

  /** TranslationKey.keyCode referansı */
  @Column(name = "body_key", nullable = false, length = 255)
  private String bodyKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationImportance importance = NotificationImportance.NORMAL;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_type", nullable = false, length = 20)
  private NotificationDeliveryType deliveryType = NotificationDeliveryType.INSTANT;

  @Column(name = "grouping_window_minutes")
  private Integer groupingWindowMinutes = 5;

  @Override
  protected String getModuleCode() {
    return "NTMPL";
  }

  public static NotificationTemplate create(
      String eventType,
      NotificationChannel channel,
      String titleKey,
      String bodyKey,
      NotificationImportance importance,
      NotificationDeliveryType deliveryType) {
    var tmpl = new NotificationTemplate();
    tmpl.eventType = eventType;
    tmpl.channel = channel;
    tmpl.titleKey = titleKey;
    tmpl.bodyKey = bodyKey;
    tmpl.importance = importance;
    tmpl.deliveryType = deliveryType;
    // CRITICAL → her zaman INSTANT
    if (importance == NotificationImportance.CRITICAL) {
      tmpl.deliveryType = NotificationDeliveryType.INSTANT;
    }
    return tmpl;
  }
}
