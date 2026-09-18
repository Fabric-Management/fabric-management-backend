package com.fabricmanagement.flowboard.routing.domain.port.in;

import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.routing.domain.RoutingRecords.Evaluation;
import java.util.UUID;

/** Invoked in the provisioning transaction by the future ORDER_COVER consumer (5b-3). */
public interface GovernedTaskRoutingPort {
  Evaluation evaluate(UUID tenantId, RoutingPoolKey poolKey, UUID taskId);
}
