package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Görevle ilgili yapılan tüm eylemlerin detaylı denetim kaydı. */
@Entity
@Table(schema = "flowboard", name = "task_activity_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskActivityLog extends BaseEntity {

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(name = "user_id")
  private UUID userId; // null ise sistem kullanıcısıdır

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private TaskAction action;

  @Column(name = "old_value", length = 255)
  private String oldValue;

  @Column(name = "new_value", length = 255)
  private String newValue;

  @Column(columnDefinition = "TEXT")
  private String metadata; // JSONB

  public TaskActivityLog(
      UUID tenantId,
      UUID taskId,
      UUID userId,
      TaskAction action,
      String oldValue,
      String newValue,
      String metadata) {
    this.setTenantId(tenantId);
    this.taskId = taskId;
    this.userId = userId;
    this.action = action;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.metadata = metadata;
  }

  @Override
  protected String getModuleCode() {
    return "TSK";
  }
}
