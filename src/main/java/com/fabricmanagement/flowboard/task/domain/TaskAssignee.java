package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bir task'a yapılan atama kaydı.
 *
 * <p>Kullanıcı veya departman bazında atama yapılabilir. WIP limiti SELF atamasında zorunlu kontrol
 * gerektirir.
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — Bölüm 5 TaskAssignee
 */
@Entity
@Table(schema = "flowboard", name = "task_assignee")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskAssignee extends BaseEntity {

  @Column(name = "task_id", nullable = false, updatable = false)
  private UUID taskId;

  /** Atanan kullanıcı — null ise departman bazlı atama. */
  @Column(name = "user_id")
  private UUID userId;

  /** Atanan departman — null ise kullanıcı bazlı atama. */
  @Column(name = "department_id")
  private UUID departmentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "assigned_by", nullable = false, length = 10)
  private AssignedBy assignedBy;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  @Override
  protected String getModuleCode() {
    return "TASGN";
  }

  /** Kullanıcıya atama. */
  public static TaskAssignee assignToUser(UUID taskId, UUID userId, AssignedBy assignedBy) {
    var assignee = new TaskAssignee();
    assignee.taskId = taskId;
    assignee.userId = userId;
    assignee.assignedBy = assignedBy;
    assignee.assignedAt = Instant.now();
    return assignee;
  }

  /** Departmana atama. */
  public static TaskAssignee assignToDepartment(
      UUID taskId, UUID departmentId, AssignedBy assignedBy) {
    var assignee = new TaskAssignee();
    assignee.taskId = taskId;
    assignee.departmentId = departmentId;
    assignee.assignedBy = assignedBy;
    assignee.assignedAt = Instant.now();
    return assignee;
  }
}
