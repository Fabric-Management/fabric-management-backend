package com.fabricmanagement.notification.hub.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kullanıcı bildirim kanal tercihleri. CRITICAL eventler → tercih yok sayılır, her zaman tüm
 * kanallar kullanılır.
 */
@Entity
@Table(
    schema = "notification",
    name = "user_notification_preference",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotificationPreference extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(nullable = false)
  private boolean inApp = true;

  @Column(nullable = false)
  private boolean email = true;

  @Column(nullable = false)
  private boolean push = true;

  @Override
  protected String getModuleCode() {
    return "UNPREF";
  }

  public static UserNotificationPreference createDefault(
      UUID tenantId, UUID userId, String eventType) {
    var pref = new UserNotificationPreference();
    pref.setTenantId(tenantId);
    pref.userId = userId;
    pref.eventType = eventType;
    return pref;
  }

  public void update(boolean inApp, boolean email, boolean push) {
    this.inApp = inApp;
    this.email = email;
    this.push = push;
  }
}
