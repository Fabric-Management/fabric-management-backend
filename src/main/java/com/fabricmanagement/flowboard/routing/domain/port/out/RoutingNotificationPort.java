package com.fabricmanagement.flowboard.routing.domain.port.out;

import java.util.UUID;

public interface RoutingNotificationPort {
  void deliver(UUID tenantId, UUID recipientId, UUID deliveryKey, UUID failureId, UUID taskId);
}
