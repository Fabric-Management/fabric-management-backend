package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Task üzerinde kişisel veya sistem taraflı kurulan hatırlatmalar. */
@Entity
@Table(schema = "flowboard", name = "task_reminder")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskReminder extends BaseEntity {

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "reminder_type", nullable = false, length = 30)
  private ReminderType reminderType;

  @Column(name = "trigger_at", nullable = false)
  private OffsetDateTime triggerAt;

  @Column(name = "offset_minutes")
  private Integer offsetMinutes;

  @Column(length = 500)
  private String message;

  @Column(name = "is_sent", nullable = false)
  private boolean isSent = false;

  @Column(name = "sent_at")
  private OffsetDateTime sentAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReminderChannel channel;

  public TaskReminder(
      UUID tenantId,
      UUID taskId,
      UUID userId,
      ReminderType reminderType,
      OffsetDateTime triggerAt,
      Integer offsetMinutes,
      String message,
      ReminderChannel channel) {
    this.setTenantId(tenantId);
    this.taskId = taskId;
    this.userId = userId;
    this.reminderType = reminderType;
    this.triggerAt = triggerAt;
    this.offsetMinutes = offsetMinutes;
    this.message = message;
    this.channel = channel;
  }

  @Override
  protected String getModuleCode() {
    return "TSK";
  }

  public void markAsSent(Clock clock) {
    this.isSent = true;
    this.sentAt = OffsetDateTime.now(clock);
  }
}
