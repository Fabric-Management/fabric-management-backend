package com.fabricmanagement.sales.salesorder.domain.port;

import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped source facts used to record durable order-cover follow reasons. */
public interface OrderCoverFollowQueryPort {
  Optional<UUID> caseForTask(UUID tenantId, UUID taskId);

  Optional<UUID> orderCreator(UUID tenantId, UUID caseId);

  Optional<SettlementSource> settlement(UUID tenantId, UUID caseId, UUID resultId);

  record SettlementSource(UUID resultId, UUID actorId, String actorKind) {}
}
