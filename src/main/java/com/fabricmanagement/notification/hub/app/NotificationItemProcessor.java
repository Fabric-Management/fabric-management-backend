package com.fabricmanagement.notification.hub.app;

import com.fabricmanagement.notification.hub.app.adapter.email.EmailNotificationSender;
import com.fabricmanagement.notification.hub.domain.*;
import com.fabricmanagement.notification.hub.infra.repository.*;
import com.fabricmanagement.notification.hub.infra.websocket.InAppNotificationSender;
import com.fabricmanagement.notification.i18n.app.TranslationService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tek bir bildirim kuyruğu kaydını işleyen servis.
 *
 * <p>Scheduler'dan ayrılmıştır — böylece {@code @Transactional} Spring AOP proxy üzerinden doğru
 * çalışır (self-invocation sorunu yok).
 *
 * <p>Kanal gönderim:
 *
 * <ul>
 *   <li>IN_APP → WebSocket (SimpMessagingTemplate)
 *   <li>EMAIL → EmailOutboxService (Transactional Outbox pattern)
 *   <li>PUSH → Stub (FCM/APNs — Faz 7 devamı)
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationItemProcessor {

  private final NotificationQueueRepository queueRepo;
  private final NotificationLogRepository logRepo;
  private final NotificationTemplateRepository templateRepo;
  private final TranslationService translationService;
  private final InAppNotificationSender inAppSender;
  private final EmailNotificationSender emailSender;
  private final NotificationUserQueryService userQueryService;

  /**
   * PROCESSING'de takılı kalan kaydı kurtarır (satır kilidi ile — eşzamanlı güncellemede optimistic
   * lock çakışması olmaz).
   */
  @Transactional
  public void recoverStuckProcessingItem(UUID queueId, Instant stuckBefore) {
    NotificationQueue item = queueRepo.findByIdWithWriteLock(queueId).orElse(null);
    if (item == null || item.getStatus() != NotificationQueueStatus.PROCESSING) {
      return;
    }
    if (item.getUpdatedAt() == null || !item.getUpdatedAt().isBefore(stuckBefore)) {
      return;
    }
    item.markFailed("Stuck in PROCESSING — recovered by scheduler");
    queueRepo.save(item);
  }

  /**
   * Tek bir kuyruktaki bildirimi işler.
   *
   * <p>Akış: PENDING → PROCESSING → translate → send → log → SENT. Hata olursa: retryCount++ → 3'te
   * FAILED.
   *
   * <p>{@code queueId} üzerinden satır kilidi ile yeniden yüklenir; scheduler veya çoklu instance
   * ortamında aynı kayıt için optimistic locking hatası riski azalır.
   */
  @Transactional
  public void processItem(UUID queueId) {
    NotificationQueue item = queueRepo.findByIdWithWriteLock(queueId).orElse(null);
    if (item == null
        || item.getStatus() != NotificationQueueStatus.PENDING
        || item.getRetryCount() >= 3) {
      return;
    }

    item.markProcessing();
    queueRepo.save(item);

    try {
      var templateOpt =
          templateRepo.findByEventTypeAndChannel(item.getEventType(), item.getChannel());

      if (templateOpt.isEmpty()) {
        item.markFailed(
            "Template not found: event=" + item.getEventType() + " channel=" + item.getChannel());
        queueRepo.save(item);
        return;
      }

      var template = templateOpt.get();

      String title =
          translationService.translateAndRender(
              item.getTenantId(), item.getLocale(), template.getTitleKey(), item.getPayload());
      String body =
          translationService.translateAndRender(
              item.getTenantId(), item.getLocale(), template.getBodyKey(), item.getPayload());

      // Kanal bazlı gönderim
      switch (item.getChannel()) {
        case IN_APP -> sendInApp(item, title, body);
        case EMAIL -> sendEmail(item, title, body);
        case PUSH -> sendPush(item, title, body);
      }

      // Log kaydı
      var notifLog =
          NotificationLog.from(
              item.getTenantId(),
              item.getRecipientId(),
              item.getEventType(),
              item.getChannel(),
              item.getImportance(),
              title,
              body,
              item.getLocale(),
              extractReferenceId(item),
              extractReferenceType(item),
              null);
      logRepo.save(notifLog);

      item.markSent();
      queueRepo.save(item);

    } catch (Exception ex) {
      String errorMsg = ex.getMessage();
      if (errorMsg != null && errorMsg.length() > 500) {
        errorMsg = errorMsg.substring(0, 500) + "...";
      }

      log.error(
          "Notification send failed: recipient={} event={} channel={} attempt={}",
          item.getRecipientId(),
          item.getEventType(),
          item.getChannel(),
          item.getRetryCount() + 1,
          ex);
      item.markFailed(errorMsg);
      queueRepo.save(item);
    }
  }

  // ---- Kanal Gönderim ----

  private void sendInApp(NotificationQueue item, String title, String body) {
    inAppSender.send(
        item.getRecipientId(),
        Map.of(
            "eventType",
            item.getEventType(),
            "importance",
            item.getImportance().name(),
            "title",
            title,
            "body",
            body,
            "queueId",
            item.getId() != null ? item.getId().toString() : ""));
  }

  private void sendEmail(NotificationQueue item, String title, String body) {
    userQueryService
        .findEmailByUserId(item.getRecipientId())
        .ifPresentOrElse(
            email -> emailSender.send(item.getTenantId(), email, title, body),
            () ->
                log.warn(
                    "EMAIL notification skipped — no email found for userId={}",
                    item.getRecipientId()));
  }

  private void sendPush(NotificationQueue item, String title, String body) {
    userQueryService
        .findPushTokenByUserId(item.getRecipientId())
        .ifPresentOrElse(
            token ->
                log.info(
                    "PUSH notification stub — token={}*** title={}",
                    token.substring(0, Math.min(6, token.length())),
                    title),
            () ->
                log.debug(
                    "PUSH notification skipped — no push token for userId={}",
                    item.getRecipientId()));
  }

  // ---- Yardımcı ----

  private UUID extractReferenceId(NotificationQueue item) {
    String refId = item.getPayload().get("referenceId");
    if (refId == null) return null;
    try {
      return UUID.fromString(refId);
    } catch (Exception e) {
      return null;
    }
  }

  private String extractReferenceType(NotificationQueue item) {
    return item.getPayload().get("referenceType");
  }
}
