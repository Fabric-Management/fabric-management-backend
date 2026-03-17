package com.fabricmanagement.notification.hub.domain;

/** Bildirim kuyruğu durumları. */
public enum NotificationQueueStatus {
  PENDING,
  PROCESSING,
  SENT,
  FAILED
}
