package com.fabricmanagement.flowboard.task.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.flowboard.automation.app.AutomationEngine;
import com.fabricmanagement.flowboard.automation.domain.AutomationTriggerType;
import com.fabricmanagement.flowboard.common.websocket.BoardWebSocketEventType;
import com.fabricmanagement.flowboard.common.websocket.BoardWebSocketPublisher;
import com.fabricmanagement.flowboard.task.domain.Task;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.flowboard.task.domain.event.TaskAssignedEvent;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import com.fabricmanagement.platform.user.domain.SystemUser;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskEventListenerTest {
  @Test
  void governedSystemRoutingEventDoesNotIntroduceLegacyAutomationOrWebSocketEffects() {
    var websocket = mock(BoardWebSocketPublisher.class);
    var automation = mock(AutomationEngine.class);
    var tasks = mock(TaskRepository.class);
    var task = mock(Task.class);
    UUID taskId = UUID.randomUUID();
    when(task.getTaskType()).thenReturn(TaskType.ORDER_COVER);
    when(tasks.findById(taskId)).thenReturn(Optional.of(task));
    var listener = new TaskEventListener(websocket, automation, tasks);

    listener.onTaskAssigned(
        new TaskAssignedEvent(
            UUID.randomUUID(),
            taskId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            SystemUser.ID,
            TaskAssignedEvent.Origin.ROUTING_EVALUATION));

    verifyNoInteractions(websocket, automation);
  }

  @Test
  void existingSystemAssignmentStillPublishesLegacyAutomationAndWebSocketEffects() {
    var websocket = mock(BoardWebSocketPublisher.class);
    var automation = mock(AutomationEngine.class);
    var tasks = mock(TaskRepository.class);
    var task = mock(Task.class);
    UUID taskId = UUID.randomUUID();
    UUID boardId = UUID.randomUUID();
    when(task.getTaskType()).thenReturn(TaskType.ORDER_COVER);
    when(task.getBoardId()).thenReturn(boardId);
    when(task.getId()).thenReturn(taskId);
    when(tasks.findById(taskId)).thenReturn(Optional.of(task));
    var listener = new TaskEventListener(websocket, automation, tasks);

    listener.onTaskAssigned(
        new TaskAssignedEvent(
            UUID.randomUUID(), taskId, UUID.randomUUID(), UUID.randomUUID(), SystemUser.ID));

    verify(websocket).publish(eq(boardId), eq(BoardWebSocketEventType.TASK_ASSIGNED), any());
    verify(automation).evaluate(eq(task), eq(AutomationTriggerType.TASK_ASSIGNED), any());
  }
}
