package com.fabricmanagement.production.core.workorder.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public contract through which sales asks production to hold a line's running work (A12). */
public interface WorkOrderHoldPort {

  /**
   * Requests a hold on every running work order of the line. Refused (WorkOrderHoldException) when
   * nothing runs or a hold is already open.
   */
  List<HoldView> request(
      UUID tenantId, UUID salesOrderId, UUID salesOrderLineId, String reason, UUID requestedBy);

  List<HoldView> forLine(UUID tenantId, UUID salesOrderLineId);

  /** True when every running work order of the line is confirmed stopped. */
  boolean isStoppedFor(UUID tenantId, UUID salesOrderLineId);

  record HoldView(
      UUID id,
      UUID workOrderId,
      String workOrderNumber,
      String status,
      String requestReason,
      UUID requestedBy,
      Instant requestedAt,
      String stopNote,
      Instant confirmedAt,
      String resumeNote,
      Instant resumedAt) {}
}
