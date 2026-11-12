package com.fabricmanagement.flowboard.routing.app.adapter;

import com.fabricmanagement.flowboard.routing.domain.port.out.RoutingNotificationPort;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.notification.hub.app.NotificationContext;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tells a routing manager that a task found nobody to route to. The wording names the task by its
 * title; the identifiers travel only as the notification's reference.
 */
@Component
@RequiredArgsConstructor
public class RoutingNotificationAdapter implements RoutingNotificationPort {
  private final NotificationHubService hub;
  private final TaskRepository taskRepository;

  @Override
  public void deliver(
      UUID tenantId, UUID recipientId, UUID deliveryKey, UUID failureId, UUID taskId) {
    String taskTitle = taskRepository.findById(taskId).map(Task::getTitle).orElse("");
    hub.deliverMandatoryInApp(
        NotificationContext.of(
            tenantId,
            recipientId,
            NotificationEventType.ROUTING_FAILURE,
            Map.of(
                "taskTitle", taskTitle,
                "failureId", failureId.toString(),
                "taskId", taskId.toString()),
            taskId,
            "TASK"),
        deliveryKey);
  }
}
