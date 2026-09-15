package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A subject that remains unresolved by an active task execution. */
@Entity
@Table(schema = "flowboard", name = "task_affected_subject")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskAffectedSubject extends BaseEntity {

  @Column(name = "task_id", nullable = false, updatable = false)
  private UUID taskId;

  @Column(name = "subject_type", nullable = false, length = 80)
  private String subjectType;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  public static TaskAffectedSubject create(UUID taskId, String subjectType, UUID subjectId) {
    if (taskId == null || subjectType == null || subjectType.isBlank() || subjectId == null) {
      throw new IllegalArgumentException("Task and typed subject are required");
    }
    var subject = new TaskAffectedSubject();
    subject.taskId = taskId;
    subject.subjectType = subjectType;
    subject.subjectId = subjectId;
    return subject;
  }

  @Override
  protected String getModuleCode() {
    return "TSUB";
  }
}
