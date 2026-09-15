package com.fabricmanagement.flowboard.task.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Complete input to the single Task provisioning writer. */
public record TaskCreation(
    UUID boardId,
    String title,
    String description,
    TaskType taskType,
    ModuleType moduleType,
    Priority priority,
    LocalDate deadline,
    BigDecimal estimatedHours,
    String entityType,
    UUID entityId,
    String sourceType,
    UUID sourceId,
    String generationKey,
    Set<TaskSubject> affectedSubjects) {

  public TaskCreation {
    affectedSubjects = affectedSubjects == null ? Set.of() : Set.copyOf(affectedSubjects);
  }
}
