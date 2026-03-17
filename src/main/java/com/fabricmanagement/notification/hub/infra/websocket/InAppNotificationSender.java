package com.fabricmanagement.notification.hub.infra.websocket;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * IN_APP kanal servisi — WebSocket üzerinden gerçek zamanlı bildirim gönderimi.
 *
 * <p>Her kullanıcı /user/queue/notifications yolundan kendi bildirimlerini alır.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationSender {

  private final SimpMessagingTemplate messagingTemplate;

  /**
   * Kullanıcıya gerçek zamanlı bildirim gönderir.
   *
   * @param recipientId Alıcı kullanıcı UUID (Spring Security principal ile eşleşmeli)
   * @param payload Gönderilecek payload (title, body, eventType vb.)
   */
  public void send(UUID recipientId, Map<String, Object> payload) {
    try {
      messagingTemplate.convertAndSendToUser(
          recipientId.toString(), "/queue/notifications", payload);
      log.debug("IN_APP notification sent to user={}", recipientId);
    } catch (Exception ex) {
      log.error("Failed to send IN_APP notification to user={}", recipientId, ex);
      throw ex; // queue processor retry mekanizmasına devret
    }
  }

  /** Tenant tüm bağlı kullanıcılarına genel bildirim (CRITICAL eventler). */
  public void broadcast(UUID tenantId, Map<String, Object> payload) {
    try {
      messagingTemplate.convertAndSend("/topic/tenant/" + tenantId, payload);
      log.debug("Broadcast notification sent to tenant={}", tenantId);
    } catch (Exception ex) {
      log.error("Failed to broadcast notification to tenant={}", tenantId, ex);
    }
  }
}
