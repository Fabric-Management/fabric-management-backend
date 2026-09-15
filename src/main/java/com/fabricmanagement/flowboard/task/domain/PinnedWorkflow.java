package com.fabricmanagement.flowboard.task.domain;

import java.util.UUID;

/** Immutable identity of the workflow definition used by one task execution. */
public record PinnedWorkflow(UUID definitionId, int version) {
  public PinnedWorkflow {
    if (definitionId == null || version < 1) {
      throw new IllegalArgumentException(
          "A workflow definition id and positive version are required");
    }
  }
}
