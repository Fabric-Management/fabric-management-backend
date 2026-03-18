package com.fabricmanagement.notification.hub.app;

import java.util.Map;
import java.util.UUID;

/**
 * Bir event'ten bildirim oluşturmak için gereken tüm bilgileri taşır.
 *
 * @param tenantId Tenant kimliği
 * @param recipientId Alıcı kullanıcı UUID
 * @param eventType Event tipi (ör: WORK_ORDER_PENDING_APPROVAL)
 * @param payload Şablon parametreleri {key → value} map'i
 * @param referenceId İlgili entity UUID (ör: workOrder.id)
 * @param referenceType İlgili entity tipi (ör: WORK_ORDER)
 */
public record NotificationContext(
    UUID tenantId,
    UUID recipientId,
    String eventType,
    Map<String, String> payload,
    UUID referenceId,
    String referenceType) {

  public static NotificationContext of(
      UUID tenantId, UUID recipientId, String eventType, Map<String, String> payload) {
    return new NotificationContext(tenantId, recipientId, eventType, payload, null, null);
  }

  public static NotificationContext of(
      UUID tenantId,
      UUID recipientId,
      String eventType,
      Map<String, String> payload,
      UUID referenceId,
      String referenceType) {
    return new NotificationContext(
        tenantId, recipientId, eventType, payload, referenceId, referenceType);
  }
}
