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

/** Tekrarlayan görevlerin şablonu (Örn: Her Pazartesi "Sistem Kontrolü" taskı aç). */
@Entity
@Table(schema = "flowboard", name = "recurring_task_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecurringTaskTemplate extends BaseEntity {

  @Column(name = "board_id", nullable = false)
  private UUID boardId;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "task_type", nullable = false, length = 50)
  private TaskType taskType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private Priority priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RecurringFrequency frequency;

  @Column(name = "cron_expression", length = 100)
  private String cronExpression;

  @Column(name = "interval_value")
  private Integer intervalValue;

  @Column(name = "target_assignee_id")
  private UUID targetAssigneeId;

  @Column(name = "target_group_id")
  private UUID targetGroupId;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @Column(name = "next_trigger_at")
  private OffsetDateTime nextTriggerAt;

  @Column(name = "last_spawned_at")
  private OffsetDateTime lastSpawnedAt;

  @Column(name = "last_spawned_task_id")
  private UUID lastSpawnedTaskId;

  public RecurringTaskTemplate(
      UUID tenantId,
      UUID boardId,
      String title,
      String description,
      TaskType taskType,
      Priority priority,
      RecurringFrequency frequency,
      String cronExpression,
      Integer intervalValue,
      UUID targetAssigneeId,
      UUID targetGroupId,
      OffsetDateTime nextTriggerAt) {
    this.setTenantId(tenantId);
    this.boardId = boardId;
    this.title = title;
    this.description = description;
    this.taskType = taskType;
    this.priority = priority;
    this.frequency = frequency;
    this.cronExpression = cronExpression;
    this.intervalValue = intervalValue;
    this.targetAssigneeId = targetAssigneeId;
    this.targetGroupId = targetGroupId;
    this.nextTriggerAt = nextTriggerAt;
  }

  @Override
  protected String getModuleCode() {
    return "RCTK"; // ReCurring TasK
  }

  public void markAsSpawned(UUID newTaskId, OffsetDateTime nextTrigger, Clock clock) {
    this.lastSpawnedTaskId = newTaskId;
    this.lastSpawnedAt = OffsetDateTime.now(clock);
    this.nextTriggerAt = nextTrigger;
  }

  public void toggleActive(boolean active) {
    this.isActive = active;
  }
}
