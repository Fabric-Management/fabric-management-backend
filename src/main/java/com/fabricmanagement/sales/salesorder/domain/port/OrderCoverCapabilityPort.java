package com.fabricmanagement.sales.salesorder.domain.port;

import java.util.*;

public interface OrderCoverCapabilityPort {
  Snapshot evaluate(
      UUID tenantId,
      UUID orderId,
      UUID taskId,
      UUID actorId,
      boolean caseOpen,
      boolean actionableEvidence);

  record Snapshot(
      Long taskVersion,
      String taskState,
      List<UUID> directAssigneeIds,
      boolean allowed,
      String blockedReason) {
    public Snapshot {
      directAssigneeIds = List.copyOf(directAssigneeIds);
    }
  }
}
