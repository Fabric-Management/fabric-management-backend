package com.fabricmanagement.flowboard.task.dto;

import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Task detay yanıtı.
 *
 * @param labels Aktif etiketler (TaskLabelAssignment + TaskLabel)
 */
public record TaskResponse(
    UUID id,
    String taskNumber,
    UUID boardId,
    UUID boardGroupId,
    String title,
    String description,
    TaskType taskType,
    ModuleType moduleType,
    Priority priority,
    Integer priorityScore,
    TaskStatus status,
    LocalDate deadline,
    BigDecimal estimatedHours,
    BigDecimal actualHours,
    boolean isOverdue,
    Instant startedAt,
    Instant completedAt,
    String entityType,
    UUID entityId,
    List<TaskAssigneeResponse> assignees,
    List<LabelResponse> labels) {

  public static TaskResponse from(
      Task task,
      LocalDate today,
      List<TaskAssigneeResponse> assignees,
      List<LabelResponse> labels) {
    return new TaskResponse(
        task.getId(),
        task.getTaskNumber(),
        task.getBoardId(),
        task.getBoardGroupId(),
        task.getTitle(),
        task.getDescription(),
        task.getTaskType(),
        task.getModuleType(),
        task.getPriority(),
        task.getPriorityScore(),
        task.getStatus(),
        task.getDeadline(),
        task.getEstimatedHours(),
        task.getActualHours(),
        task.isOverdue(today),
        task.getStartedAt(),
        task.getCompletedAt(),
        task.getEntityType(),
        task.getEntityId(),
        assignees != null ? assignees : List.of(),
        labels != null ? labels : List.of());
  }

  public static TaskResponse from(Task task, LocalDate today) {
    return from(task, today, List.of(), List.of());
  }

  @Deprecated(forRemoval = true)
  public static TaskResponse from(Task task) {
    return from(task, LocalDate.now());
  }
}
