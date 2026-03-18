package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Task üzerinde harcanan zamanın takibi (Mondays.com Timeline usulü). */
@Entity
@Table(schema = "flowboard", name = "task_time_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskTimeEntry extends BaseEntity {

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "started_at", nullable = false)
  private OffsetDateTime startedAt;

  @Column(name = "ended_at")
  private OffsetDateTime endedAt;

  @Column(name = "duration_minutes")
  private Integer durationMinutes;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 20)
  private TimeEntryType entryType;

  @Column(length = 500)
  private String note;

  // Active Timer oluşturmak için
  public TaskTimeEntry(UUID tenantId, UUID taskId, UUID userId, Clock clock) {
    this.setTenantId(tenantId);
    this.taskId = taskId;
    this.userId = userId;
    this.startedAt = OffsetDateTime.now(clock);
    this.entryType = TimeEntryType.TIMER;
  }

  // Manuel entry oluşturmak için
  public TaskTimeEntry(
      UUID tenantId,
      UUID taskId,
      UUID userId,
      OffsetDateTime startedAt,
      Integer durationMinutes,
      String note) {
    this.setTenantId(tenantId);
    this.taskId = taskId;
    this.userId = userId;
    this.startedAt = startedAt;
    this.endedAt = startedAt.plusMinutes(durationMinutes);
    this.durationMinutes = durationMinutes;
    this.entryType = TimeEntryType.MANUAL;
    this.note = note;
  }

  @Override
  protected String getModuleCode() {
    return "TSK";
  }

  public void stopTimer(Clock clock) {
    if (this.endedAt != null) {
      throw new IllegalStateException("Timer is already stopped");
    }
    this.endedAt = OffsetDateTime.now(clock);
    this.durationMinutes = Math.max(0, (int) Duration.between(startedAt, endedAt).toMinutes());
  }
}
