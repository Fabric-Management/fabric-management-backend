package com.fabricmanagement.flowboard.common.websocket;

/**
 * Board WebSocket event tipleri — STOMP /topic/board/{boardId} kanalında iletilir.
 *
 * <p>Docs: {@code 07-flowboard/board-task.md} — WebSocket — Gerçek Zamanlı Board Güncellemeleri
 */
public enum BoardWebSocketEventType {
  TASK_CREATED,
  TASK_STATUS_CHANGED,
  TASK_ASSIGNED,
  TASK_PRIORITY_UPDATED,
  TASK_COMMENTED,
  TASK_BLOCKED,
  TASK_COMPLETED,
  TASK_MOVED_GROUP,
  CHECKLIST_UPDATED,
  TIMER_STARTED,
  TIMER_STOPPED,
  LABEL_CHANGED
}
