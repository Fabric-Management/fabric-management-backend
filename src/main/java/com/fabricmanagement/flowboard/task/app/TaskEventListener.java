package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.automation.app.AutomationEngine;
import com.fabricmanagement.flowboard.automation.domain.AutomationContext;
import com.fabricmanagement.flowboard.automation.domain.AutomationTriggerType;
import com.fabricmanagement.flowboard.common.websocket.BoardWebSocketEventType;
import com.fabricmanagement.flowboard.common.websocket.BoardWebSocketPublisher;
import com.fabricmanagement.flowboard.task.domain.event.TaskAssignedEvent;
import com.fabricmanagement.flowboard.task.domain.event.TaskChecklistCompletedEvent;
import com.fabricmanagement.flowboard.task.domain.event.TaskCreatedEvent;
import com.fabricmanagement.flowboard.task.domain.event.TaskStatusChangedEvent;
import com.fabricmanagement.flowboard.task.infra.repository.TaskRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Task domain event'lerini dinler ve sırasıyla:
 *
 * <ol>
 *   <li>WebSocket yayını yapar — {@code AFTER_COMMIT} ([B2 FIX])
 *   <li>AutomationEngine'i tetikler — kural değerlendirmesi
 * </ol>
 *
 * <p>[B2 FIX] {@code @TransactionalEventListener(phase = AFTER_COMMIT)} kullanılarak transaction
 * rollback durumunda WS false positive yayınlanması önlenir.
 *
 * <p>[EV1 FIX] İlk defa domain event dinleniyor — AutomationEngine entegre edildi.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskEventListener {

  private final BoardWebSocketPublisher wsPublisher;
  private final AutomationEngine automationEngine;
  private final TaskRepository taskRepo;

  // =========================================================================
  // TASK CREATED
  // =========================================================================

  /** [B2 FIX] WS yayını AFTER_COMMIT'te yapılır — transaction rollback olursa yayın olmaz. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onTaskCreated(TaskCreatedEvent event) {
    log.debug("TaskEventListener.onTaskCreated: taskId={}", event.getTaskId());

    // 1. Önce hızlı işlem olan WebSocket yayını
    wsPublisher.publish(
        event.getBoardId(),
        BoardWebSocketEventType.TASK_CREATED,
        Map.of(
            "taskId", event.getTaskId().toString(),
            "taskNumber", event.getTaskNumber(),
            "status", event.getStatus()));

    // 2. AutomationEngine çağrısı (DB'den temiz güncel state çekilerek LazyInit önlenir)
    taskRepo
        .findById(event.getTaskId())
        .ifPresent(
            task ->
                automationEngine.evaluate(
                    task,
                    AutomationTriggerType.TASK_CREATED,
                    AutomationContext.initial(task.getId(), task.getBoardId())));
  }

  // =========================================================================
  // TASK ASSIGNED
  // =========================================================================

  /** Atama sonrası WS yayını + AutomationEngine. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onTaskAssigned(TaskAssignedEvent event) {
    log.debug("TaskEventListener.onTaskAssigned: taskId={}", event.getTaskId());

    taskRepo
        .findById(event.getTaskId())
        .ifPresent(
            task -> {
              // 1. Hızlı işlem: WS yayını
              wsPublisher.publish(
                  task.getBoardId(),
                  BoardWebSocketEventType.TASK_ASSIGNED,
                  Map.of(
                      "taskId", task.getId().toString(),
                      "assignedUserId",
                          event.getAssignedUserId() != null
                              ? event.getAssignedUserId().toString()
                              : "UNASSIGNED",
                      "assignedByUserId",
                          event.getAssignedByUserId() != null
                              ? event.getAssignedByUserId().toString()
                              : "SYSTEM"));

              // 2. AutomationEngine çağrısı
              automationEngine.evaluate(
                  task,
                  AutomationTriggerType.TASK_ASSIGNED,
                  AutomationContext.initial(task.getId(), task.getBoardId()));
            });
  }

  // =========================================================================
  // TASK STATUS CHANGED
  // =========================================================================

  /** [B2 FIX] WS yayını + AutomationEngine tetikleme — AFTER_COMMIT'te. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onTaskStatusChanged(TaskStatusChangedEvent event) {
    log.debug(
        "TaskEventListener.onTaskStatusChanged: taskId={} {}→{}",
        event.getTaskId(),
        event.getOldStatus(),
        event.getNewStatus());

    // WS yayını
    wsPublisher.publish(
        event.getBoardId(),
        BoardWebSocketEventType.TASK_STATUS_CHANGED,
        Map.of(
            "taskId", event.getTaskId().toString(),
            "oldStatus", event.getOldStatus(),
            "newStatus", event.getNewStatus(),
            "changedBy",
                event.getChangedByUserId() != null
                    ? event.getChangedByUserId().toString()
                    : "SYSTEM"));

    // AutomationEngine tetikle — STATUS_CHANGED kuralları [X2 FIX: oldStatus/newStatus geçir]
    taskRepo
        .findById(event.getTaskId())
        .ifPresent(
            task ->
                automationEngine.evaluate(
                    task,
                    AutomationTriggerType.STATUS_CHANGED,
                    AutomationContext.initial(task.getId(), task.getBoardId()),
                    event.getOldStatus(),
                    event.getNewStatus()));
  }

  // =========================================================================
  // TASK UNASSIGNED
  // =========================================================================

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onTaskUnassigned(
      com.fabricmanagement.flowboard.task.domain.event.TaskUnassignedEvent event) {
    log.debug("TaskEventListener.onTaskUnassigned: taskId={}", event.getTaskId());

    taskRepo
        .findById(event.getTaskId())
        .ifPresent(
            task -> {
              // WS yayını
              wsPublisher.publish(
                  task.getBoardId(),
                  BoardWebSocketEventType.TASK_UNASSIGNED,
                  Map.of(
                      "taskId", task.getId().toString(),
                      "unassignedUserId", event.getUnassignedUserId().toString(),
                      "unassignedByUserId", event.getUnassignedByUserId().toString()));
            });
  }

  // =========================================================================
  // CHECKLIST COMPLETED (Faz 3.1)
  // =========================================================================

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  public void onChecklistCompleted(TaskChecklistCompletedEvent event) {
    log.debug(
        "TaskEventListener.onChecklistCompleted: taskId={} checklistId={}",
        event.getTaskId(),
        event.getChecklistId());

    taskRepo
        .findById(event.getTaskId())
        .ifPresent(
            task -> {
              wsPublisher.publish(
                  task.getBoardId(),
                  BoardWebSocketEventType.CHECKLIST_UPDATED,
                  Map.of(
                      "taskId", task.getId().toString(),
                      "checklistId", event.getChecklistId().toString()));

              automationEngine.evaluate(
                  task,
                  AutomationTriggerType.CHECKLIST_COMPLETED,
                  AutomationContext.initial(task.getId(), task.getBoardId()));
            });
  }
}
