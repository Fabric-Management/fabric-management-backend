package com.fabricmanagement.production.core.batch.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Public contract through which sales asks for a technical lot-compatibility decision (A04). */
public interface LotCompatibilityRequestPort {

  /** Opens a request, or returns the open one for the same source and lot set. */
  RequestView request(
      UUID tenantId,
      Collection<UUID> batchIds,
      UUID productId,
      UUID customerId,
      String sourceType,
      UUID sourceId,
      String note,
      UUID requestedBy);

  /** Every request raised by a source, newest first. */
  List<RequestView> forSource(UUID tenantId, UUID sourceId);

  /** Withdraws the open requests of a source (the customer's answer was superseded). */
  void withdrawOpen(UUID tenantId, UUID sourceId, UUID actor);

  record RequestView(
      UUID id,
      List<UUID> batchIds,
      String status,
      Instant requestedAt,
      Instant resolvedAt,
      String resolutionNote) {}
}
