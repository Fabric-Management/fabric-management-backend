package com.fabricmanagement.flowboard.task.domain;

import java.util.Set;
import java.util.UUID;

/** Typed success or side-effect-free business rejection returned by a domain adapter. */
public sealed interface DomainTaskActionResult {
  record Accepted(String resultType, UUID resultId, Set<TaskSubject> remainingSubjects)
      implements DomainTaskActionResult {
    public Accepted {
      if (resultType == null || resultType.isBlank() || resultId == null) {
        throw new IllegalArgumentException(
            "Accepted domain result requires a typed result reference");
      }
      remainingSubjects = remainingSubjects == null ? Set.of() : Set.copyOf(remainingSubjects);
    }

    public Accepted(String resultType, UUID resultId) {
      this(resultType, resultId, Set.of());
    }
  }

  record Rejected(String code, String message) implements DomainTaskActionResult {
    public Rejected {
      if (code == null || code.isBlank()) {
        throw new IllegalArgumentException("Business rejection code is required");
      }
    }
  }
}
