package com.fabricmanagement.flowboard.routing.app.adapter;

import com.fabricmanagement.flowboard.routing.domain.port.out.RoutingNotificationPort;
import com.fabricmanagement.notification.hub.app.NotificationContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoutingNotificationAdapter implements RoutingNotificationPort {
  private final NotificationHubService hub;

  @Override
  public void deliver(
      UUID tenantId, UUID recipientId, UUID deliveryKey, UUID failureId, UUID taskId) {
    hub.deliverMandatoryInApp(
        NotificationContext.of(
            tenantId,
            recipientId,
            NotificationEventType.ROUTING_FAILURE,
            Map.of("failureId", failureId.toString(), "taskId", taskId.toString()),
            taskId,
            "TASK"),
        deliveryKey);
  }
}
