package com.fabricmanagement.flowboard.task.dto;

import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.RecurringFrequency;
import com.fabricmanagement.flowboard.task.domain.RecurringTaskTemplate;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecurringTaskTemplateDto(
    UUID id,
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
    boolean active,
    OffsetDateTime nextTriggerAt,
    OffsetDateTime lastSpawnedAt,
    UUID lastSpawnedTaskId) {

  public static RecurringTaskTemplateDto from(RecurringTaskTemplate t) {
    return new RecurringTaskTemplateDto(
        t.getId(),
        t.getBoardId(),
        t.getTitle(),
        t.getDescription(),
        t.getTaskType(),
        t.getPriority(),
        t.getFrequency(),
        t.getCronExpression(),
        t.getIntervalValue(),
        t.getTargetAssigneeId(),
        t.getTargetGroupId(),
        t.isActive(),
        t.getNextTriggerAt(),
        t.getLastSpawnedAt(),
        t.getLastSpawnedTaskId());
  }
}
