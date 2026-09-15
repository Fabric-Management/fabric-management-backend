package com.fabricmanagement.flowboard.task.domain;

import java.util.UUID;

/** Trusted, already authenticated command envelope for a typed Task action. */
public record TaskActionCommand(
    UUID taskId,
    UUID actorId,
    String idempotencyKey,
    String actionKey,
    String payloadFingerprint,
    long expectedVersion) {
  public TaskActionCommand {
    if (taskId == null
        || actorId == null
        || idempotencyKey == null
        || idempotencyKey.isBlank()
        || actionKey == null
        || actionKey.isBlank()
        || payloadFingerprint == null
        || !payloadFingerprint.matches("[a-fA-F0-9]{64}")
        || expectedVersion < 0) {
      throw new IllegalArgumentException("Task action command envelope is invalid");
    }
  }
}
