package com.fabricmanagement.notification.hub.app;

import com.fabricmanagement.notification.hub.domain.*;
import com.fabricmanagement.notification.hub.infra.repository.*;
import com.fabricmanagement.notification.i18n.app.TranslationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merkezi bildirim orchestrator.
 *
 * <p>Akış: Event → Template bul → Tercih kontrolü → Locale çöz → Render → Queue → Log
 *
 * <p>CRITICAL eventler: tercih yok sayılır, anında tüm kanallar. HIGH / NORMAL eventler: kullanıcı
 * tercihine göre kanal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationHubService {

  private final NotificationTemplateRepository templateRepo;
  private final NotificationQueueRepository queueRepo;
  private final NotificationLogRepository logRepo;
  private final UserNotificationPreferenceRepository prefRepo;
  private final TranslationService translationService;

  /**
   * Bir alıcı için bildirim oluşturur ve kuyruğa ekler.
   *
   * <p>Şablon bulunamazsa sessizce atlanır — event entegrasyonu kademeli eklendiği için.
   */
  @Transactional
  public void notify(NotificationContext ctx) {
    List<NotificationTemplate> templates = templateRepo.findAllByEventType(ctx.eventType());

    if (templates.isEmpty()) {
      log.warn("No notification template found for eventType={} — skipping", ctx.eventType());
      return;
    }

    String locale = translationService.resolveLocaleForUser(ctx.tenantId(), ctx.recipientId());

    // N+1 fix: tercihi tek sorguda al, tüm template'ler için kullan
    var preference =
        prefRepo.findByUserIdAndEventType(ctx.recipientId(), ctx.eventType()).orElse(null);

    for (NotificationTemplate template : templates) {
      if (!shouldSend(preference, template)) {
        log.debug(
            "User preference disabled — skipping channel={} for eventType={}",
            template.getChannel(),
            ctx.eventType());
        continue;
      }

      enqueue(ctx, template, locale);
    }
  }

  /** Birden fazla alıcı için toplu bildirim (ör: tüm production departmanı). */
  @Transactional
  public void notifyAll(
      List<UUID> recipientIds, UUID tenantId, String eventType, Map<String, String> payload) {
    notifyAll(recipientIds, tenantId, eventType, payload, null, null);
  }

  @Transactional
  public void notifyAll(
      List<UUID> recipientIds,
      UUID tenantId,
      String eventType,
      Map<String, String> payload,
      UUID referenceId,
      String referenceType) {
    for (UUID recipientId : recipientIds) {
      notify(
          NotificationContext.of(
              tenantId, recipientId, eventType, payload, referenceId, referenceType));
    }
  }

  /** Bildirim okundu olarak işaret. */
  @Transactional
  public void markRead(UUID notificationLogId, UUID recipientId) {
    logRepo
        .findById(notificationLogId)
        .filter(nl -> nl.getRecipientId().equals(recipientId))
        .ifPresent(NotificationLog::markRead);
  }

  /** Tüm bildirimleri okundu işaret. */
  @Transactional
  public int markAllRead(UUID recipientId) {
    return logRepo.markAllReadForRecipient(recipientId);
  }

  /** Kullanıcının okunmamış bildirim sayısı. */
  @Transactional(readOnly = true)
  public long countUnread(UUID recipientId) {
    return logRepo.countUnreadByRecipient(recipientId);
  }

  // ---- Yardımcı metodlar ----

  private boolean shouldSend(UserNotificationPreference preference, NotificationTemplate template) {
    // CRITICAL → tercih yok sayılır, her zaman gönder
    if (template.getImportance() == NotificationImportance.CRITICAL) {
      return true;
    }

    // Tercih kaydı yoksa varsayılan: gönder
    if (preference == null) {
      return true;
    }

    return switch (template.getChannel()) {
      case IN_APP -> preference.isInApp();
      case EMAIL -> preference.isEmail();
      case PUSH -> preference.isPush();
    };
  }

  private void enqueue(NotificationContext ctx, NotificationTemplate template, String locale) {
    var queue =
        NotificationQueue.create(
            ctx.tenantId(),
            ctx.recipientId(),
            ctx.eventType(),
            template.getChannel(),
            template.getImportance(),
            template.getDeliveryType(),
            ctx.payload(),
            locale);
    queueRepo.save(queue);

    log.debug(
        "Queued notification: recipient={} event={} channel={} importance={}",
        ctx.recipientId(),
        ctx.eventType(),
        template.getChannel(),
        template.getImportance());
  }
}
