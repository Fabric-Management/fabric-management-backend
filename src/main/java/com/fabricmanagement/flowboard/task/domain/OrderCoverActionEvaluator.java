package com.fabricmanagement.flowboard.task.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pure, shared decision for the order-cover presentation capability. */
public final class OrderCoverActionEvaluator {
  private OrderCoverActionEvaluator() {}

  public static Result evaluate(Inputs inputs) {
    Objects.requireNonNull(inputs, "inputs");
    if (!inputs.caseOpen()) return Result.blocked("CASE_CLOSED");
    if (!inputs.taskPresent()) return Result.blocked("UNASSIGNED");
    if (!inputs.candidacyAllowed()) return Result.blocked("PERMISSION_DENIED");
    if (!inputs.poolMember()) return Result.blocked("OUTSIDE_ROUTING_POOL");
    if (!inputs.writeScopeAllowed()) return Result.blocked("PERMISSION_DENIED");
    if (!inputs.assigned()) return Result.blocked("UNASSIGNED");
    if (!inputs.directAssigneeIds().contains(inputs.actorId())) {
      return Result.blocked("ASSIGNED_ELSEWHERE");
    }
    if (!inputs.actionableEvidence()) return Result.blocked("EVIDENCE_UNKNOWN");
    return new Result(true, null);
  }

  public record Inputs(
      boolean caseOpen,
      boolean taskPresent,
      boolean candidacyAllowed,
      boolean poolMember,
      boolean writeScopeAllowed,
      boolean assigned,
      List<UUID> directAssigneeIds,
      UUID actorId,
      boolean actionableEvidence) {
    public Inputs {
      directAssigneeIds = List.copyOf(directAssigneeIds == null ? List.of() : directAssigneeIds);
    }
  }

  public record Result(boolean allowed, String blockedReason) {
    private static Result blocked(String reason) {
      return new Result(false, reason);
    }
  }
}
