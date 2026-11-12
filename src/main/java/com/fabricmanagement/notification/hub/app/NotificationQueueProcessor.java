package com.fabricmanagement.notification.hub.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.app.NotificationQueueScanner.QueueRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bildirim kuyruğu scheduler'ı.
 *
 * <p>Periyodik olarak PENDING kayıtları alır ve {@link NotificationItemProcessor} ile işler.
 *
 * <p>Ayrıca 5+ dakikadır PROCESSING statüsünde kalan kayıtları kurtarır (stuck recovery).
 *
 * <p>Bu iş bir tenant'a ait bir thread'de çalışmaz. Vadesi gelen kayıtlar {@link
 * NotificationQueueScanner} ile tenant'lar arası okunur; her kayıt kendi tenant bağlamında işlenir.
 * Kuyruğu doğrudan JPA ile sorgulamak RLS altında hiçbir satır döndürmez ve kuyruk boş görünür.
 *
 * <p>İşleme mantığı ayrı bir {@code @Service}'te tutulur — böylece Spring AOP proxy üzerinden
 * {@code @Transactional} doğru çalışır.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationQueueProcessor {

  @Value("${application.notification.queue.batch-size:50}")
  private int batchSize;

  @Value("${application.notification.queue.stuck-threshold-minutes:5}")
  private int stuckThresholdMinutes;

  private final NotificationQueueScanner scanner;
  private final NotificationItemProcessor itemProcessor;

  /** Ana kuyruk işleme döngüsü — her 10 saniyede bir çalışır. */
  @Scheduled(fixedDelayString = "${application.notification.queue.poll-interval-ms:10000}")
  public void processQueue() {
    // 1. Stuck recovery — PROCESSING'de takılı kalmış kayıtları kurtar
    try {
      recoverStuckItems();
    } catch (Exception ex) {
      log.error("Notification stuck-recovery scan failed: {}", ex.getMessage(), ex);
    }

    // 2. PENDING kayıtları al ve işle
    List<QueueRef> pending;
    try {
      pending = scanner.pending(batchSize);
    } catch (Exception ex) {
      log.error("Notification queue scan failed: {}", ex.getMessage(), ex);
      return;
    }

    if (pending.isEmpty()) {
      return;
    }

    log.info("Processing {} pending notification(s)", pending.size());

    for (QueueRef item : pending) {
      try {
        TenantContext.executeInTenantContext(
            item.tenantId(), () -> itemProcessor.processItem(item.id()));
      } catch (Exception ex) {
        // itemProcessor kendi içinde hataları yönetiyor,
        // ama beklenmedik bir hata olursa (ör: DB bağlantısı) diğer kayıtları engellemesin
        log.error(
            "Unexpected error processing notification queueId={}: {}", item.id(), ex.getMessage());
      }
    }
  }

  /**
   * 5+ dakikadır PROCESSING statüsünde kalan kayıtları PENDING'e geri çeker.
   *
   * <p>Uygulama çökerse veya thread kesilirse kayıtlar PROCESSING'de kalır. Bu metot onları
   * kurtarır.
   */
  private void recoverStuckItems() {
    Instant threshold = Instant.now().minus(stuckThresholdMinutes, ChronoUnit.MINUTES);
    List<QueueRef> stuck = scanner.stuck(threshold);

    if (!stuck.isEmpty()) {
      log.warn(
          "Recovering {} stuck PROCESSING notification(s) (threshold={}min)",
          stuck.size(),
          stuckThresholdMinutes);

      for (QueueRef item : stuck) {
        try {
          TenantContext.executeInTenantContext(
              item.tenantId(),
              () -> itemProcessor.recoverStuckProcessingItem(item.id(), threshold));
          log.warn(
              "Recovered stuck notification: queueId={} event={}", item.id(), item.eventType());
        } catch (Exception ex) {
          log.error(
              "Failed to recover stuck notification queueId={}: {}", item.id(), ex.getMessage());
        }
      }
    }
  }
}
