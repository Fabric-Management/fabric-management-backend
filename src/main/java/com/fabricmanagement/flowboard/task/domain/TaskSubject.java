package com.fabricmanagement.flowboard.task.domain;

import java.util.UUID;

/** Stable typed reference used for a task's authoritative unresolved scope. */
public record TaskSubject(String type, UUID id) {
  public TaskSubject {
    if (type == null || type.isBlank() || id == null) {
      throw new IllegalArgumentException("Subject type and id are required");
    }
  }
}
