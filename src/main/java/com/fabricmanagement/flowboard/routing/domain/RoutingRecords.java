package com.fabricmanagement.flowboard.routing.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable JDBC snapshots. The task-state composite key is not a surrogate JPA identity. */
public final class RoutingRecords {
  private RoutingRecords() {}

  public record Pool(UUID id, RoutingPoolKey key, long revision) {}

  public record Member(UUID userId, boolean active) {}

  public record Failure(
      UUID id,
      UUID taskId,
      RoutingPoolKey poolKey,
      RoutingFailureReason reason,
      UUID userId,
      Long poolRevision,
      Instant occurredAt,
      Instant resolvedAt) {}

  public record Alert(
      UUID id,
      UUID recipientId,
      String channel,
      String status,
      String cancelReason,
      int attempts,
      String lastError,
      Instant deliveredAt) {}

  public record Evaluation(boolean changed, int opened, int resolved) {}

  public record Repair(
      int evaluated,
      int changed,
      int failuresOpened,
      int failuresResolved,
      boolean deliveryComplete) {}
}
