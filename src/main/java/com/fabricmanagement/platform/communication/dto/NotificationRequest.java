package com.fabricmanagement.platform.communication.dto;

import com.fabricmanagement.platform.communication.domain.NotificationDeliveryChannel;
import com.fabricmanagement.platform.communication.domain.NotificationType;
import java.util.UUID;
import lombok.Builder;

/**
 * Internal request for creating an in-app notification.
 *
 * <p>recipientId null = broadcast to all admins of the tenant.
 */
@Builder
public record NotificationRequest(
    UUID tenantId,
    UUID recipientId,
    NotificationType type,
    String title,
    String message,
    UUID referenceId,
    String referenceType,
    NotificationDeliveryChannel channel) {

  public NotificationRequest {
    channel = channel != null ? channel : NotificationDeliveryChannel.IN_APP;
  }
}
