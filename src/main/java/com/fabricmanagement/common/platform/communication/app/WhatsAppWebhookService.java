package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.platform.communication.domain.DeliveryStatus;
import com.fabricmanagement.common.platform.communication.infra.repository.VerificationLogRepository;
import com.fabricmanagement.common.platform.communication.infra.webhook.WhatsAppWebhookPayload;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WhatsApp Webhook Service - Processes delivery status updates from Meta.
 *
 * <p>Handles webhook notifications from WhatsApp Business API for message status updates.
 *
 * <p><b>Status Mapping:</b>
 *
 * <ul>
 *   <li>sent → SENT
 *   <li>delivered → DELIVERED
 *   <li>read → DELIVERED (we don't track read separately)
 *   <li>failed → FAILED
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookService {

  private final VerificationLogRepository verificationLogRepository;

  /**
   * Process WhatsApp webhook payload and update VerificationLog status.
   *
   * @param payload Webhook payload from Meta
   */
  @Transactional
  public void processWebhook(WhatsAppWebhookPayload payload) {
    if (payload == null || payload.getEntry() == null) {
      log.warn("Received empty webhook payload");
      return;
    }

    log.debug("Processing WhatsApp webhook with {} entries", payload.getEntry().size());

    for (WhatsAppWebhookPayload.Entry entry : payload.getEntry()) {
      if (entry.getChanges() == null) {
        continue;
      }

      for (WhatsAppWebhookPayload.Change change : entry.getChanges()) {
        if (change.getValue() == null || change.getValue().getStatuses() == null) {
          continue;
        }

        processStatuses(change.getValue().getStatuses());
      }
    }
  }

  /** Process status updates from webhook. */
  private void processStatuses(List<WhatsAppWebhookPayload.Status> statuses) {
    for (WhatsAppWebhookPayload.Status status : statuses) {
      String messageId = status.getId();
      String statusValue = status.getStatus();

      if (messageId == null || statusValue == null) {
        log.warn("Status update missing messageId or status value");
        continue;
      }

      DeliveryStatus deliveryStatus = mapWhatsAppStatus(statusValue);

      log.info(
          "Updating message status: messageId={}, whatsappStatus={}, deliveryStatus={}",
          messageId,
          statusValue,
          deliveryStatus);

      int updated =
          verificationLogRepository.updateStatusByExternalMessageId(messageId, deliveryStatus);

      if (updated > 0) {
        log.info("✅ Updated VerificationLog status for messageId={}", messageId);
      } else {
        log.warn("⚠️ No VerificationLog found for messageId={}", messageId);
      }

      if (status.getErrors() != null && !status.getErrors().isEmpty()) {
        logErrors(messageId, status.getErrors());
      }
    }
  }

  /**
   * Map WhatsApp status to internal DeliveryStatus.
   *
   * @param whatsappStatus WhatsApp status (sent, delivered, read, failed)
   * @return Internal delivery status
   */
  private DeliveryStatus mapWhatsAppStatus(String whatsappStatus) {
    return switch (whatsappStatus.toLowerCase()) {
      case "sent" -> DeliveryStatus.SENT;
      case "delivered", "read" -> DeliveryStatus.DELIVERED;
      case "failed" -> DeliveryStatus.FAILED;
      default -> {
        log.warn("Unknown WhatsApp status: {}, mapping to PENDING", whatsappStatus);
        yield DeliveryStatus.PENDING;
      }
    };
  }

  /** Log errors from webhook status. */
  private void logErrors(String messageId, List<WhatsAppWebhookPayload.Error> errors) {
    for (WhatsAppWebhookPayload.Error error : errors) {
      log.error(
          "WhatsApp delivery error for messageId={}: code={}, title={}, message={}",
          messageId,
          error.getCode(),
          error.getTitle(),
          error.getMessage());
    }
  }
}
