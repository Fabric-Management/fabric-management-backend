package com.fabricmanagement.notification.hub.dto;

import com.fabricmanagement.notification.hub.domain.NotificationChannel;
import com.fabricmanagement.notification.hub.domain.NotificationImportance;
import com.fabricmanagement.notification.hub.domain.NotificationLog;
import java.time.Instant;
import java.util.UUID;

public record NotificationLogResponse(
    UUID id,
    String eventType,
    NotificationChannel channel,
    NotificationImportance importance,
    String title,
    String body,
    String locale,
    Instant sentAt,
    boolean isRead,
    Instant readAt,
    boolean isClicked,
    String actionTaken,
    UUID referenceId,
    String referenceType,
    UUID groupId) {

  public static NotificationLogResponse from(NotificationLog log) {
    return new NotificationLogResponse(
        log.getId(),
        log.getEventType(),
        log.getChannel(),
        log.getImportance(),
        log.getTitle(),
        log.getBody(),
        log.getLocale(),
        log.getSentAt(),
        log.isRead(),
        log.getReadAt(),
        log.isClicked(),
        log.getActionTaken(),
        log.getReferenceId(),
        log.getReferenceType(),
        log.getGroupId());
  }
}
