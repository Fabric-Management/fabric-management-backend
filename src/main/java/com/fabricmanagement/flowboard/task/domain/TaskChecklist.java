package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Task'in alt görevi. */
@Entity
@Table(schema = "flowboard", name = "task_checklist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskChecklist extends BaseEntity {

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(name = "is_completed", nullable = false)
  private boolean isCompleted = false;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "completed_by_user_id")
  private UUID completedByUserId;

  @Column(name = "display_order", nullable = false)
  private int displayOrder = 1;

  public TaskChecklist(UUID tenantId, UUID taskId, String title, int displayOrder) {
    this.setTenantId(tenantId);
    this.taskId = taskId;
    this.title = title;
    this.displayOrder = displayOrder;
  }

  @Override
  protected String getModuleCode() {
    return "TSK";
  }

  public void complete(UUID completedByUserId, Clock clock) {
    this.isCompleted = true;
    this.completedByUserId = completedByUserId;
    this.completedAt = OffsetDateTime.now(clock);
  }

  public void uncomplete() {
    this.isCompleted = false;
    this.completedByUserId = null;
    this.completedAt = null;
  }

  public void updateTitle(String newTitle) {
    this.title = newTitle;
  }
}
