package com.fabricmanagement.notification.hub.app;

import com.fabricmanagement.notification.hub.domain.*;
import com.fabricmanagement.notification.hub.infra.repository.*;
import com.fabricmanagement.notification.i18n.app.TranslationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Slf4j
public class NotificationHubService {

  private final NotificationTemplateRepository templateRepo;
  private final NotificationQueueRepository queueRepo;
  private final NotificationLogRepository logRepo;
  private final UserNotificationPreferenceRepository prefRepo;
  private final TranslationService translationService;
  private final JdbcTemplate jdbc;

  public NotificationHubService(
      NotificationTemplateRepository templateRepo,
      NotificationQueueRepository queueRepo,
      NotificationLogRepository logRepo,
      UserNotificationPreferenceRepository prefRepo,
      TranslationService translationService,
      @Qualifier("dataSource") DataSource dataSource) {
    this.templateRepo = templateRepo;
    this.queueRepo = queueRepo;
    this.logRepo = logRepo;
    this.prefRepo = prefRepo;
    this.translationService = translationService;
    // The mandatory insert must enlist in the alert's primary JPA transaction, not the
    // independently configured systemJdbcTemplate/BYPASSRLS connection.
    this.jdbc = new JdbcTemplate(dataSource);
  }

  /** Synchronous durable in-app delivery; must join the caller's status-update transaction. */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void deliverMandatoryInApp(NotificationContext ctx, UUID deliveryKey) {
    java.util.Objects.requireNonNull(deliveryKey, "deliveryKey");
    var template =
        templateRepo
            .findByTenantIdAndEventTypeAndChannelAndIsActiveTrue(
                ctx.tenantId(), ctx.eventType(), NotificationChannel.IN_APP)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Mandatory in-app template missing: " + ctx.eventType()));
    String locale = translationService.resolveLocaleForUser(ctx.tenantId(), ctx.recipientId());
    var rendered =
        NotificationRenderer.render(
            translationService, ctx.tenantId(), locale, template, ctx.payload());
    jdbc.update(
        """
        INSERT INTO notification.notification_log
          (id, tenant_id, uid, recipient_id, event_type, channel, importance, title, body, locale,
           sent_at, is_read, is_clicked, reference_id, reference_type, delivery_key,
           is_active, created_at, updated_at, version)
        VALUES (gen_random_uuid(), ?, gen_random_uuid()::text, ?, ?, 'IN_APP', ?, ?, ?, ?,
           now(), false, false, ?, ?, ?, true, now(), now(), 0)
        ON CONFLICT (tenant_id, delivery_key) WHERE delivery_key IS NOT NULL DO NOTHING
        """,
        ctx.tenantId(),
        ctx.recipientId(),
        ctx.eventType(),
        template.getImportance().name(),
        rendered.title(),
        rendered.body(),
        locale,
        ctx.referenceId(),
        ctx.referenceType(),
        deliveryKey);
  }

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

  /** Kullanıcının bildirim tercihini günceller. */
  @Transactional
  public void updatePreference(
      UUID tenantId, UUID userId, String eventType, boolean inApp, boolean email, boolean push) {
    prefRepo
        .findByUserIdAndEventType(userId, eventType)
        .ifPresentOrElse(
            pref -> {
              pref.update(inApp, email, push);
              prefRepo.save(pref);
            },
            () -> {
              var pref = UserNotificationPreference.createDefault(tenantId, userId, eventType);
              pref.update(inApp, email, push);
              prefRepo.save(pref);
            });
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
