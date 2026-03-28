package com.fabricmanagement.platform.communication.dto;

import com.fabricmanagement.platform.communication.domain.Notification;
import com.fabricmanagement.platform.communication.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * DTO for notification API responses.
 *
 * <p>referenceType + referenceId → frontend deep link for navigation.
 */
@Data
@Builder
public class NotificationDto {

  private UUID id;
  private NotificationType type;
  private String title;
  private String message;
  private UUID referenceId;
  private String referenceType;
  private boolean isRead;
  private Instant readAt;
  private Instant createdAt;

  public static NotificationDto from(Notification n) {
    return NotificationDto.builder()
        .id(n.getId())
        .type(n.getType())
        .title(n.getTitle())
        .message(n.getMessage())
        .referenceId(n.getReferenceId())
        .referenceType(n.getReferenceType())
        .isRead(Boolean.TRUE.equals(n.getIsRead()))
        .readAt(n.getReadAt())
        .createdAt(n.getCreatedAt())
        .build();
  }
}
