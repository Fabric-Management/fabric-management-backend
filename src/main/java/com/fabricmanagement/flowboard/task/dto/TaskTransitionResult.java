package com.fabricmanagement.flowboard.task.dto;

import com.fabricmanagement.flowboard.task.domain.TaskStatus;
import com.fabricmanagement.flowboard.task.domain.TaskSubject;
import com.fabricmanagement.flowboard.task.domain.TaskTransitionOutcome;
import java.util.Set;
import java.util.UUID;

public record TaskTransitionResult(
    TaskTransitionOutcome outcome,
    String resultType,
    UUID resultId,
    String rejectionCode,
    String rejectionMessage,
    boolean replayed,
    UUID taskId,
    long taskVersion,
    TaskStatus taskState,
    Set<TaskSubject> remainingSubjects) {
  public TaskTransitionResult {
    remainingSubjects = remainingSubjects == null ? Set.of() : Set.copyOf(remainingSubjects);
  }

  public TaskTransitionResult(
      TaskTransitionOutcome outcome,
      String resultType,
      UUID resultId,
      String rejectionCode,
      String rejectionMessage,
      boolean replayed) {
    this(
        outcome,
        resultType,
        resultId,
        rejectionCode,
        rejectionMessage,
        replayed,
        null,
        0,
        null,
        Set.of());
  }
}
