package com.fabricmanagement.production.core.stockunit.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Public production contract for piece-level hard allocation at sales-order confirmation (SOI D4,
 * A05, TK-4). An allocation is all-or-nothing: either every named piece is held for the line or
 * nothing changes and the outcome names why.
 */
public interface PieceAllocationPort {

  AllocationOutcome allocate(AllocationRequest request);

  /**
   * Row-locks the given lots in one deterministic (sorted) pass inside the caller's transaction, so
   * that several {@link #allocate} calls for the lines of one order never take lot locks in
   * different orders than another confirmation running at the same time.
   */
  void lockLots(UUID tenantId, Collection<UUID> batchIds);

  List<PieceAllocationView> activeForLine(UUID tenantId, UUID salesOrderLineId);

  List<PieceAllocationView> activeForOrder(UUID tenantId, UUID salesOrderId);

  /** Releases every active allocation of the line; returns how many pieces were released. */
  int releaseForLine(UUID tenantId, UUID salesOrderLineId, UUID actorId, String reason);

  record AllocationRequest(
      UUID tenantId,
      UUID salesOrderId,
      UUID salesOrderLineId,
      UUID actorId,
      List<PieceRef> pieces) {}

  /** A piece and the version it had when the proposal was built. */
  record PieceRef(UUID stockUnitId, UUID batchId, Long expectedVersion) {}

  record AllocationOutcome(
      boolean allocated, String failureCode, List<UUID> unavailablePieceIds, int allocatedPieces) {

    public static AllocationOutcome success(int pieces) {
      return new AllocationOutcome(true, null, List.of(), pieces);
    }

    public static AllocationOutcome failure(String code, List<UUID> pieces) {
      return new AllocationOutcome(false, code, List.copyOf(pieces), 0);
    }
  }

  record PieceAllocationView(
      UUID allocationId,
      UUID stockUnitId,
      UUID batchId,
      UUID salesOrderLineId,
      BigDecimal quantity,
      String unit,
      Instant allocatedAt) {}

  /** Failure codes; the sales side maps them to its own error codes. */
  final class Failures {
    public static final String PIECE_TAKEN = "PIECE_TAKEN";
    public static final String PIECE_CHANGED = "PIECE_CHANGED";
    public static final String PIECE_NOT_FOUND = "PIECE_NOT_FOUND";
    public static final String LOT_QUANTITY_INSUFFICIENT = "LOT_QUANTITY_INSUFFICIENT";
    public static final String MEASURE_UNRESOLVED = "MEASURE_UNRESOLVED";

    private Failures() {}
  }
}
